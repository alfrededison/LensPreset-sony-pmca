package com.lenspreset.adapter;

import android.hardware.Camera;
import android.os.Handler;
import android.util.Pair;
import android.view.SurfaceHolder;

import com.lenspreset.core.engine.Recommendation;
import com.lenspreset.core.engine.ValueSeeker;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.optics.Shutter;
import com.sony.scalar.hardware.CameraEx;

import java.io.IOException;
import java.util.List;

/**
 * The camera, through Sony's CameraEx. The only class that talks to the hardware: it opens the
 * camera, runs the live view, tracks the aperture / shutter / focal length the camera reports,
 * and applies a {@link Recommendation}. Create and use it on the UI thread.
 *
 * <p>Every call is wrapped: a method missing on some firmware ({@link NoSuchMethodError}) or a
 * refused value becomes a FAILED item in the {@link ApplyReport}, never a crash.
 */
public final class SonyCamera {
    public interface Listener {
        /** Something the camera reports changed: aperture, shutter, focal length, or the lens itself. */
        void onLiveChanged(boolean lensChanged);
    }

    public interface ApplyCallback {
        void onApplied(ApplyReport report);
    }

    /** What the lens says about itself; null fields when the body does not tell. */
    public static final class LensReading {
        public final String name;
        public final Long id;
        public final int focalWideRaw;
        public final int focalTeleRaw;

        LensReading(String name, Long id, int focalWideRaw, int focalTeleRaw) {
            this.name = name;
            this.id = id;
            this.focalWideRaw = focalWideRaw;
            this.focalTeleRaw = focalTeleRaw;
        }
    }

    /**
     * Wait for a stepped value to report back before treating the step as "no movement". Generous,
     * because a report arriving after the timeout would be credited to the next step.
     */
    private static final int STEP_TIMEOUT_MS = 1000;
    /** Let a new exposure mode settle before stepping aperture or shutter in it. */
    private static final int MODE_SETTLE_MS = 400;
    private static final int MAX_STEPS = 40;
    /**
     * Close enough to the target, in stops. Under a sixth of a stop: an A5100 reporting f/1.7 for a
     * target of f/1.8 (0.165 stops off) was left there at 0.17.
     */
    private static final double TOLERANCE_STOPS = 0.12;
    /** Device type of a lens in {@link CameraEx.EquipmentCallback} (CameraEx.DEVICE_TYPE_LENS). */
    private static final int DEVICE_TYPE_LENS = 1;

    private final CameraEx cam;
    private final Handler handler = new Handler();
    private Listener listener;

    private Double aperture;
    private Double widestAperture;
    private Shutter shutter;
    private int focalRaw;
    private Seek seek;
    private String lensProblem;
    /** The settle delay of the apply in progress, removed when a newer apply starts. */
    private Runnable pendingSettle;
    /** Bumped by every apply; an older apply's steps and callback are dropped. */
    private int applyGeneration;
    private boolean closed;

    private SonyCamera(CameraEx cam) {
        this.cam = cam;
    }

    /**
     * Opens the camera; throws when it is not available (another app, or not a Sony body). Then, as
     * BetterManual does: register the surface callback, call {@link #startDirectShutter()}, and start
     * the preview from {@code surfaceCreated}.
     */
    public static SonyCamera open(Listener listener) {
        CameraEx cam = CameraEx.open(0, null);
        SonyCamera c = new SonyCamera(cam);
        c.listener = listener;
        c.registerListeners();
        return c;
    }

    /** Lets the shutter button shoot while the app is in front. */
    public void startDirectShutter() {
        try {
            cam.startDirectShutter();
        } catch (Throwable ignored) {
            // Without direct shutter the camera still shoots through its own pipeline.
        }
    }

    private void registerListeners() {
        try {
            cam.setApertureChangeListener(new CameraEx.ApertureChangeListener() {
                @Override
                public void onApertureChange(CameraEx.ApertureInfo info, CameraEx c) {
                    aperture = SonyValues.fNumber(info.currentAperture);
                    widestAperture = SonyValues.fNumber(info.currentAvailableMin);
                    if (seek != null && seek.aperture && aperture != null) seek.reported(Scales.apertureStops(aperture));
                    notifyLive(false);
                }
            });
        } catch (Throwable ignored) {
            // No aperture reports: the overlay shows the target only.
        }
        try {
            cam.setShutterSpeedChangeListener(new CameraEx.ShutterSpeedChangeListener() {
                @Override
                public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo info, CameraEx c) {
                    if (info.currentShutterSpeed_n > 0 && info.currentShutterSpeed_d > 0)
                        shutter = new Shutter(info.currentShutterSpeed_n, info.currentShutterSpeed_d);
                    if (seek != null && !seek.aperture && shutter != null) seek.reported(shutter.stops());
                    notifyLive(false);
                }
            });
        } catch (Throwable ignored) {
            // As above.
        }
        try {
            cam.setFocalLengthChangeListener(new CameraEx.FocalLengthChangeListener() {
                @Override
                public void onFocalLengthChanged(int focal, CameraEx c) {
                    focalRaw = focal;
                    notifyLive(false);
                }
            });
        } catch (Throwable ignored) {
            // Zooms are then computed at their wide end.
        }
        try {
            cam.setEquipmentCallback(new CameraEx.EquipmentCallback() {
                @Override
                public void onEquipmentChange(int device, int status, CameraEx c) {
                    if (device == DEVICE_TYPE_LENS) notifyLive(true);
                }
            });
        } catch (Throwable ignored) {
            // Lens changes are then picked up on the next resume.
        }
    }

    private void notifyLive(final boolean lensChanged) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!closed && listener != null) listener.onLiveChanged(lensChanged);
            }
        });
    }

    public void startPreview(SurfaceHolder holder) throws IOException {
        Camera c = cam.getNormalCamera();
        c.setPreviewDisplay(holder);
        c.startPreview();
    }

    public void close() {
        closed = true;
        if (seek != null) seek.cancel();
        handler.removeCallbacksAndMessages(null);
        try {
            cam.getNormalCamera().stopPreview();
        } catch (Throwable ignored) {
            // Releasing below stops it too.
        }
        try {
            cam.release();
        } catch (Throwable ignored) {
            // Nothing more to do when closing.
        }
    }

    public Double aperture() {
        return aperture;
    }

    public Double widestAperture() {
        return widestAperture;
    }

    public Shutter shutter() {
        return shutter;
    }

    public int focalRaw() {
        return focalRaw;
    }

    /** Why the last {@link #readLens()} returned null, for the log. */
    public String lensProblem() {
        return lensProblem;
    }

    public LensReading readLens() {
        lensProblem = null;
        try {
            CameraEx.LensInfo info = cam.getLensInfo();
            if (info == null) {
                lensProblem = "getLensInfo returned null";
                return null;
            }
            int wide = info.FocalLength != null ? info.FocalLength.wide : 0;
            int tele = info.FocalLength != null ? info.FocalLength.tele : 0;
            return new LensReading(info.LensName, lensId(info), wide, tele);
        } catch (Throwable t) {
            lensProblem = "getLensInfo: " + ApplyReport.describe(t);
            return null;
        }
    }

    /** LensId is missing on some firmware (ILCE-5100 3.10); see camera-adapter/stubs/optional.txt. */
    private static Long lensId(CameraEx.LensInfo info) {
        try {
            return info.LensId == 0 ? null : Long.valueOf(info.LensId);
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    // ---- applying a recommendation ----

    /** A write of one or more parameters in one {@code setParameters} call. */
    private interface Write {
        void to(Camera.Parameters p, CameraEx.ParametersModifier m);
    }

    private void write(Write w) {
        Camera.Parameters p = cam.createEmptyParameters();
        w.to(p, cam.createParametersModifier(p));
        cam.getNormalCamera().setParameters(p);
    }

    private CameraEx.ParametersModifier current() {
        return cam.createParametersModifier(cam.getNormalCamera().getParameters());
    }

    private static List<?> supported(SupportedList list) {
        try {
            return list.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private interface SupportedList {
        List<?> get();
    }

    /** Applies one setting: picks a supported value, writes it, records the outcome. */
    private void set(ApplyReport report, String key, String want, List<?> supportedValues,
                     String[] candidates, final ValueWrite write) {
        final String value = SonyValues.pick(supportedValues, candidates);
        if (value == null) {
            report.add(key, ApplyReport.Status.UNSUPPORTED, want + " not in " + ProbeReport.format(supportedValues));
            return;
        }
        try {
            write(new Write() {
                @Override
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m) {
                    write.to(p, m, value);
                }
            });
            report.add(key, ApplyReport.Status.OK, value);
        } catch (NoSuchMethodError e) {
            report.add(key, ApplyReport.Status.UNSUPPORTED, value + " not on this firmware");
        } catch (Throwable t) {
            report.add(key, ApplyReport.Status.FAILED, value + " " + ApplyReport.describe(t));
        }
    }

    private interface ValueWrite {
        void to(Camera.Parameters p, CameraEx.ParametersModifier m, String value);
    }

    private void setPlain(ApplyReport report, String key, String detail, Write w) {
        try {
            write(w);
            report.add(key, ApplyReport.Status.OK, detail);
        } catch (NoSuchMethodError e) {
            // Optional on some firmware, e.g. setISOAutoMin/Max on ILCE-5100 3.10 (stubs/optional.txt).
            report.add(key, ApplyReport.Status.UNSUPPORTED, detail + " not on this firmware");
        } catch (Throwable t) {
            report.add(key, ApplyReport.Status.FAILED, detail + " " + ApplyReport.describe(t));
        }
    }

    /**
     * Applies {@code r}. The plain settings are written at once; aperture and shutter are then
     * stepped toward their targets, and {@code done} runs with the full report when they settle.
     */
    public void apply(final Recommendation r, final ApplyCallback done) {
        if (seek != null) seek.cancel();
        seek = null;
        if (pendingSettle != null) handler.removeCallbacks(pendingSettle);
        pendingSettle = null;
        final int generation = ++applyGeneration;
        final ApplyReport report = new ApplyReport();
        Camera.Parameters now;
        final CameraEx.ParametersModifier cur;
        try {
            now = cam.getNormalCamera().getParameters();
            cur = cam.createParametersModifier(now);
        } catch (Throwable t) {
            report.add("camera", ApplyReport.Status.FAILED, ApplyReport.describe(t));
            done.onApplied(report);
            return;
        }

        final String scene = SonyValues.sceneMode(r.exposureMode);
        set(report, "exposure_mode", scene, supported(new SupportedList() {
            public List<?> get() { return cam.getNormalCamera().getParameters().getSupportedSceneModes(); }
        }), new String[]{scene}, new ValueWrite() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { p.setSceneMode(v); }
        });

        setPlain(report, "iso", r.isoAuto ? "auto" : Integer.toString(r.iso), new Write() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { m.setISOSensitivity(r.isoAuto ? 0 : r.iso); }
        });
        if (r.isoAuto && r.isoAutoRangeSettable) {
            setPlain(report, "iso_range", r.isoAutoMin + ".." + r.isoAutoMax, new Write() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m) {
                    m.setISOAutoMin(r.isoAutoMin);
                    m.setISOAutoMax(r.isoAutoMax);
                }
            });
        }

        if (r.minShutter != null) applyMinShutter(report, cur, r.minShutter);

        try {
            final int idx = SonyValues.evIndex(r.ev, now.getExposureCompensationStep(),
                    now.getMinExposureCompensation(), now.getMaxExposureCompensation());
            setPlain(report, "ev", Scales.formatEv(r.ev), new Write() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { p.setExposureCompensation(idx); }
            });
        } catch (Throwable t) {
            report.add("ev", ApplyReport.Status.FAILED, ApplyReport.describe(t));
        }

        if (r.metering != null) {
            set(report, "metering", r.metering, supported(new SupportedList() {
                public List<?> get() { return cur.getSupportedMeteringModes(); }
            }), SonyValues.meteringCandidates(r.metering), new ValueWrite() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setMeteringMode(v); }
            });
        }

        applyFocus(report, now, cur, r);

        final String[] drive = SonyValues.drive(r.drive);
        set(report, "drive", r.drive, supported(new SupportedList() {
            public List<?> get() { return cur.getSupportedDriveModes(); }
        }), new String[]{drive[0]}, new ValueWrite() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) {
                m.setDriveMode(v);
                if (drive[1] != null) m.setBurstDriveSpeed(drive[1]);
            }
        });

        set(report, "self_timer", Integer.toString(r.selfTimer), supported(new SupportedList() {
            public List<?> get() { return cur.getSupportedSelfTimers(); }
        }), new String[]{Integer.toString(r.selfTimer)}, new ValueWrite() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setSelfTimer(Integer.parseInt(v)); }
        });

        if (r.creativeStyle != null) {
            String[] styles = r.creativeStyleFallback != null
                    ? new String[]{r.creativeStyle, r.creativeStyleFallback} : new String[]{r.creativeStyle};
            set(report, "creative_style", r.creativeStyle, supported(new SupportedList() {
                public List<?> get() { return cur.getSupportedColorModes(); }
            }), styles, new ValueWrite() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setColorMode(v); }
            });
        }
        if (r.softSkin != null) {
            set(report, "soft_skin", r.softSkin, supported(new SupportedList() {
                public List<?> get() { return cur.getSupportedSoftSkinEffects(); }
            }), new String[]{SonyValues.softSkin(r.softSkin)}, new ValueWrite() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setSoftSkinEffect(v); }
            });
        }

        final boolean seekAperture = r.aperture != null && r.apertureSettable
                && (r.exposureMode == Preset.ExposureMode.APERTURE_PRIORITY || r.exposureMode == Preset.ExposureMode.MANUAL);
        final boolean seekShutter = r.shutter != null
                && (r.exposureMode == Preset.ExposureMode.SHUTTER_PRIORITY || r.exposureMode == Preset.ExposureMode.MANUAL);
        if (!seekAperture && !seekShutter) {
            done.onApplied(report);
            return;
        }
        pendingSettle = new Runnable() {
            @Override
            public void run() {
                pendingSettle = null;
                if (closed || generation != applyGeneration) return;
                final Runnable finished = new Runnable() {
                    @Override
                    public void run() {
                        if (!closed && generation == applyGeneration) done.onApplied(report);
                    }
                };
                Runnable afterAperture = new Runnable() {
                    @Override
                    public void run() {
                        if (generation != applyGeneration) return;
                        if (seekShutter) startSeek(false, r.shutter.stops(), report, finished);
                        else finished.run();
                    }
                };
                if (seekAperture) startSeek(true, Scales.apertureStops(r.aperture), report, afterAperture);
                else afterAperture.run();
            }
        };
        handler.postDelayed(pendingSettle, MODE_SETTLE_MS);
    }

    private void applyMinShutter(ApplyReport report, CameraEx.ParametersModifier cur, final Shutter min) {
        final int code = SonyValues.minShutterCode(min);
        if (code < 0) {
            report.add("min_shutter", ApplyReport.Status.UNSUPPORTED, min.format() + " has no low-limit code");
            return;
        }
        try {
            if (!cur.isSupportedAutoShutterSpeedLowLimit()) {
                report.add("min_shutter", ApplyReport.Status.UNSUPPORTED, min.format());
                return;
            }
        } catch (Throwable t) {
            report.add("min_shutter", ApplyReport.Status.FAILED, min.format() + " " + ApplyReport.describe(t));
            return;
        }
        setPlain(report, "min_shutter", min.format() + " (code " + code + ")", new Write() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { m.setAutoShutterSpeedLowLimit(code); }
        });
    }

    private void applyFocus(ApplyReport report, Camera.Parameters now, final CameraEx.ParametersModifier cur,
                            final Recommendation r) {
        final String af = SonyValues.autoFocusMode(r.focusMode);
        if (af == null) {
            setPlain(report, "focus_mode", "manual", new Write() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m) {
                    p.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
                }
            });
            return;
        }
        final boolean wasManual = CameraEx.ParametersModifier.FOCUS_MODE_MANUAL.equals(now.getFocusMode());
        set(report, "focus_mode", r.focusMode, supported(new SupportedList() {
            public List<?> get() { return cur.getSupportedAutoFocusModes(); }
        }), new String[]{af}, new ValueWrite() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) {
                // Leaving manual focus goes through Android's own focus mode first.
                if (wasManual) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                m.setAutoFocusMode(v);
            }
        });
        if (r.focusArea != null) {
            set(report, "focus_area", r.focusArea, supported(new SupportedList() {
                public List<?> get() { return cur.getSupportedFocusAreaModes(); }
            }), SonyValues.focusAreaCandidates(r.focusArea), new ValueWrite() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setFocusAreaMode(v); }
            });
        }
        final String face = r.faceDetection ? CameraEx.ParametersModifier.FACE_DETECTION_MODE_AUTO
                : CameraEx.ParametersModifier.FACE_DETECTION_MODE_OFF;
        set(report, "face", face, supported(new SupportedList() {
            public List<?> get() { return cur.getSupportedFaceDetectionModes(); }
        }), new String[]{face}, new ValueWrite() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m, String v) { m.setFaceDetectionMode(v); }
        });
    }

    /** The current value in stops, or null when the camera has not reported one. */
    private Double currentStops(boolean isAperture) {
        if (isAperture) return aperture != null ? Double.valueOf(Scales.apertureStops(aperture)) : null;
        return shutter != null ? Double.valueOf(shutter.stops()) : null;
    }

    private void startSeek(boolean isAperture, double targetStops, ApplyReport report, Runnable next) {
        if (seek != null) seek.cancel();
        seek = null;
        Double start = currentStops(isAperture);
        if (start == null) {
            report.add(isAperture ? "aperture" : "shutter", ApplyReport.Status.FAILED,
                    "the camera has not reported a current value");
            next.run();
            return;
        }
        seek = new Seek(isAperture, targetStops, report, next);
        seek.step(start);
    }

    /** Steps aperture or shutter toward a target, one click per camera report (or timeout). */
    private final class Seek implements Runnable {
        final boolean aperture;
        final double target;
        final ValueSeeker seeker;
        final ApplyReport report;
        /** Every value seen, as the camera reports it, for the log: "2.80 2.50 2.20 ...". */
        final StringBuilder trail = new StringBuilder();
        final Runnable next;
        boolean cancelled;
        /** The value when the last command was sent; a report of the same value is not an answer to it. */
        double sentAt = Double.NaN;

        Seek(boolean aperture, double target, ApplyReport report, Runnable next) {
            this.aperture = aperture;
            this.target = target;
            // As observed on an A5100: incrementAperture closes the aperture (raises the f-number),
            // incrementShutterSpeed makes the shutter faster (lowers the time).
            this.seeker = new ValueSeeker(target, TOLERANCE_STOPS, MAX_STEPS,
                    aperture ? ValueSeeker.INCREMENT : ValueSeeker.DECREMENT);
            this.report = report;
            this.next = next;
        }

        /**
         * A report from the camera's listener. Reports repeat (the aperture listener also fires when
         * only the widest available aperture changes while zooming), so one that shows no change is
         * left to the timeout, the only place "no movement" is decided.
         */
        void reported(double currentStops) {
            if (Math.abs(currentStops - sentAt) < 1e-6) return;
            step(currentStops);
        }

        void step(double currentStops) {
            if (cancelled) return;
            handler.removeCallbacks(this);
            int cmd = seeker.next(currentStops);
            sentAt = currentStops;
            trail.append(trail.length() == 0 ? "" : " ").append(String.format(java.util.Locale.US,
                    aperture ? "%.2f" : "%.4fs", aperture ? Scales.fromApertureStops(currentStops) : Math.pow(2, currentStops)));
            if (cmd == ValueSeeker.DONE) {
                finish(currentStops);
                return;
            }
            try {
                if (aperture) {
                    if (cmd == ValueSeeker.INCREMENT) cam.incrementAperture();
                    else cam.decrementAperture();
                } else {
                    if (cmd == ValueSeeker.INCREMENT) cam.incrementShutterSpeed();
                    else cam.decrementShutterSpeed();
                }
            } catch (Throwable t) {
                fail(ApplyReport.describe(t));
                return;
            }
            handler.postDelayed(this, STEP_TIMEOUT_MS);
        }

        /** Timeout: no report since the last step. */
        @Override
        public void run() {
            Double v = currentStops(aperture);
            if (v != null) step(v);
            else fail("the camera stopped reporting a value (lens removed?)");
        }

        private void release() {
            cancelled = true;
            handler.removeCallbacks(this);
            if (seek == this) seek = null;
        }

        void fail(String why) {
            release();
            report.add(aperture ? "aperture" : "shutter", ApplyReport.Status.FAILED, why);
            next.run();
        }

        void finish(double currentStops) {
            release();
            // The nearest value the camera offers counts: it cannot stop between its own steps.
            ValueSeeker.Outcome why = seeker.outcome();
            boolean ok = why == ValueSeeker.Outcome.REACHED || why == ValueSeeker.Outcome.NEAREST;
            // As the camera displays it: the API reports f/1.8 as 1.70 on an A5100 with a Sigma 30.
            String got = aperture ? Scales.formatAperture(Scales.nearestAperture(Scales.fromApertureStops(currentStops)))
                    : Scales.nearestShutter(Math.pow(2, currentStops)).format();
            report.add(aperture ? "aperture" : "shutter", ok ? ApplyReport.Status.OK : ApplyReport.Status.PARTIAL,
                    "got " + got + " in " + seeker.steps() + " steps, " + why.name().toLowerCase(java.util.Locale.US)
                            + " (" + trail + ")");
            next.run();
        }

        void cancel() {
            cancelled = true;
            handler.removeCallbacks(this);
        }
    }

    // ---- the probe ----

    /** Everything the API answers on this body, for PROBE.TXT. Reads only; changes nothing. */
    public void probe(final ProbeReport out) {
        out.section("lens");
        final CameraEx.ParametersModifier[] mod = new CameraEx.ParametersModifier[1];
        final Camera.Parameters[] params = new Camera.Parameters[1];
        out.probe("parameters", new ProbeReport.Read() {
            public Object get() {
                params[0] = cam.getNormalCamera().getParameters();
                mod[0] = cam.createParametersModifier(params[0]);
                return "ok";
            }
        });
        if (mod[0] != null) {
            out.probe("isSupportedLensInfo", new ProbeReport.Read() {
                public Object get() { return mod[0].isSupportedLensInfo(); }
            });
        }
        out.probe("getLensInfo", new ProbeReport.Read() {
            public Object get() throws IllegalAccessException {
                CameraEx.LensInfo i = cam.getLensInfo();
                if (i == null) return null;
                // Every field the firmware's LensInfo has, by reflection: the set differs by firmware.
                StringBuilder sb = new StringBuilder();
                for (java.lang.reflect.Field f : i.getClass().getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Object v = f.get(i);
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(f.getName()).append('=')
                            .append(v instanceof CameraEx.WideTele ? wideTele((CameraEx.WideTele) v) : String.valueOf(v));
                }
                return sb.toString();
            }
        });

        out.section("live");
        out.put("aperture", aperture);
        out.put("widestAperture", widestAperture);
        out.put("shutter", shutter);
        out.put("focalRaw", focalRaw);
        if (mod[0] == null) return;
        final CameraEx.ParametersModifier m = mod[0];
        final Camera.Parameters p = params[0];
        out.probe("getAperture", new ProbeReport.Read() {
            public Object get() { return m.getAperture(); }
        });
        out.probe("getShutterSpeed", new ProbeReport.Read() {
            public Object get() {
                Pair<?, ?> s = m.getShutterSpeed();
                return s == null ? null : s.first + "/" + s.second;
            }
        });

        out.section("current");
        out.probe("sceneMode", new ProbeReport.Read() { public Object get() { return p.getSceneMode(); } });
        out.probe("focusMode", new ProbeReport.Read() { public Object get() { return p.getFocusMode(); } });
        out.probe("autoFocusMode", new ProbeReport.Read() { public Object get() { return m.getAutoFocusMode(); } });
        out.probe("focusAreaMode", new ProbeReport.Read() { public Object get() { return m.getFocusAreaMode(); } });
        out.probe("faceDetectionMode", new ProbeReport.Read() { public Object get() { return m.getFaceDetectionMode(); } });
        out.probe("meteringMode", new ProbeReport.Read() { public Object get() { return m.getMeteringMode(); } });
        out.probe("isoSensitivity", new ProbeReport.Read() { public Object get() { return m.getISOSensitivity(); } });
        out.probe("autoShutterSpeedLowLimit", new ProbeReport.Read() { public Object get() { return m.getAutoShutterSpeedLowLimit(); } });
        out.probe("driveMode", new ProbeReport.Read() { public Object get() { return m.getDriveMode(); } });
        out.probe("burstDriveSpeed", new ProbeReport.Read() { public Object get() { return m.getBurstDriveSpeed(); } });
        out.probe("selfTimer", new ProbeReport.Read() { public Object get() { return m.getSelfTimer(); } });
        out.probe("colorMode", new ProbeReport.Read() { public Object get() { return m.getColorMode(); } });
        out.probe("softSkinEffect", new ProbeReport.Read() { public Object get() { return m.getSoftSkinEffect(); } });
        out.probe("exposureCompensation", new ProbeReport.Read() {
            public Object get() {
                return p.getExposureCompensation() + " (" + p.getMinExposureCompensation() + ".."
                        + p.getMaxExposureCompensation() + " step " + p.getExposureCompensationStep() + ")";
            }
        });

        out.section("supported");
        out.probe("sceneModes", new ProbeReport.Read() { public Object get() { return p.getSupportedSceneModes(); } });
        out.probe("isoSensitivities", new ProbeReport.Read() { public Object get() { return m.getSupportedISOSensitivities(); } });
        out.probe("isoAutoMaxMin", new ProbeReport.Read() { public Object get() { return m.getSupportedISOAutoMaxMin(); } });
        out.probe("autoShutterSpeedLowLimit", new ProbeReport.Read() { public Object get() { return m.isSupportedAutoShutterSpeedLowLimit(); } });
        out.probe("meteringModes", new ProbeReport.Read() { public Object get() { return m.getSupportedMeteringModes(); } });
        out.probe("autoFocusModes", new ProbeReport.Read() { public Object get() { return m.getSupportedAutoFocusModes(); } });
        out.probe("focusAreaModes", new ProbeReport.Read() { public Object get() { return m.getSupportedFocusAreaModes(); } });
        out.probe("faceDetectionModes", new ProbeReport.Read() { public Object get() { return m.getSupportedFaceDetectionModes(); } });
        out.probe("driveModes", new ProbeReport.Read() { public Object get() { return m.getSupportedDriveModes(); } });
        out.probe("burstDriveSpeeds", new ProbeReport.Read() { public Object get() { return m.getSupportedBurstDriveSpeeds(); } });
        out.probe("selfTimers", new ProbeReport.Read() { public Object get() { return m.getSupportedSelfTimers(); } });
        out.probe("colorModes", new ProbeReport.Read() { public Object get() { return m.getSupportedColorModes(); } });
        out.probe("softSkinEffects", new ProbeReport.Read() { public Object get() { return m.getSupportedSoftSkinEffects(); } });

        out.section("parameters");
        out.probe("flatten", new ProbeReport.Read() {
            public Object get() {
                String flat = p.flatten();
                for (String kv : flat.split(";")) out.raw(kv);
                return flat.length() + " chars";
            }
        });
    }

    private static String wideTele(CameraEx.WideTele wt) {
        return wt == null ? "null" : wt.wide + "/" + wt.tele;
    }
}
