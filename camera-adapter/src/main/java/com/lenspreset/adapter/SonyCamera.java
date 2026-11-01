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

    public interface ShotCallback {
        /** {@code status}: 0 taken, 1 cancelled, 2 error; -1 when the call itself failed. */
        void onShot(int status, String detail);
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
    private ShotCallback shot;
    private Runnable pendingCapture;
    private Runnable shotTimeout;
    private Runnable shotRestore;
    private int selfTimer;
    private boolean timerReady;
    /** Native AF/AE lock held by a half press while direct shutter is disabled. */
    private String quickFocus;
    private boolean halfPressed;
    /** Give direct shutter a moment to stop before a shot from code (BetterManual counts down 1 s+). */
    private static final int SHUTTER_STOP_MS = 1000;
    private static final int SHOT_TIMEOUT_MS = 10000;
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

    /**
     * Takes one picture with the current settings; used by the adb test run to check, from the photo's
     * EXIF, what the camera really shot with. BetterManual's timelapse order: a shot from code is
     * cancelled (status 1) while direct shutter (the shutter button's own path) and the preview run,
     * so both are stopped first and started again once the camera reports the shot.
     */
    public void takePicture(ShotCallback done) {
        if (closed || shot != null) {
            done.onShot(-1, closed ? "camera closed" : "capture already in progress");
            return;
        }
        final int timer = selfTimer;
        final int stopDelay = timer > 0 ? 0 : SHUTTER_STOP_MS;
        final ShotCallback capture = done;
        shot = done;
        final Runnable restore = new Runnable() {
            private boolean restored;

            @Override
            public void run() {
                if (restored || closed) return;
                restored = true;
                try {
                    startDirectShutter();
                    cam.getNormalCamera().startPreview();
                } catch (Throwable ignored) {
                    // The next resume opens the camera afresh.
                }
            }
        };
        shotRestore = restore;
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                cancelNativeShot();
                deliverShot(-1, "no shutter report after timer and " + SHOT_TIMEOUT_MS / 1000 + " s");
            }
        };
        shotTimeout = timeout;
        try {
            if (timer > 0 && !timerReady)
                throw new IllegalStateException("direct shutter is still stopping");
            if (timer > 0 && current().getSelfTimer() != timer)
                throw new IllegalStateException("self timer was not accepted by the camera");
            cam.setShutterListener(new CameraEx.ShutterListener() {
                @Override
                public void onShutter(final int status, CameraEx c) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (shot != capture) return;
                            cancelNativeShot();
                            deliverShot(status, "");
                        }
                    });
                }
            });
            if (timer == 0) {
                cam.stopDirectShutter(new CameraEx.DirectShutterStoppedCallback() {
                    @Override
                    public void onShutterStopped(CameraEx c) { }
                });
            }
            cam.getNormalCamera().stopPreview();
            pendingCapture = new Runnable() {
                @Override
                public void run() {
                    if (closed || shot != capture) return;
                    try {
                        if (timer > 0) cam.startSelfTimerShutter();
                        else cam.burstableTakePicture();
                    } catch (Throwable t) {
                        cancelNativeShot();
                        deliverShot(-1, ApplyReport.describe(t));
                    }
                }
            };
            handler.postDelayed(pendingCapture, stopDelay);
            handler.postDelayed(timeout, stopDelay + timer * 1000 + SHOT_TIMEOUT_MS);
        } catch (Throwable t) {
            cancelNativeShot();
            deliverShot(-1, ApplyReport.describe(t));
        }
    }

    private void deliverShot(int status, String detail) {
        ShotCallback s = shot;
        shot = null;
        if (pendingCapture != null) handler.removeCallbacks(pendingCapture);
        if (shotTimeout != null) handler.removeCallbacks(shotTimeout);
        pendingCapture = null;
        shotTimeout = null;
        if (!halfPressed) releaseQuickFocus();
        Runnable restore = shotRestore;
        shotRestore = null;
        if (restore != null) restore.run();
        if (s != null && !closed) s.onShot(status, detail);
    }

    private void cancelNativeShot() {
        try {
            if (selfTimer > 0) cam.cancelSelfTimerShutter();
        } catch (Throwable ignored) {
            // Still attempt to end the capture if timer cancellation is unavailable.
        }
        try {
            cam.cancelTakePicture();
        } catch (Throwable ignored) {
            // Closing the camera below is the final cleanup if cancellation is unavailable.
        }
    }

    public boolean hasSelfTimer() { return selfTimer > 0; }

    public boolean isTakingPicture() { return shot != null; }

    /** Direct shutter normally handles S1; timed mode must drive Sony's AF/AE path itself. */
    public String halfPress(boolean down) {
        if (closed || selfTimer == 0) return "ignored";
        halfPressed = down;
        if (shot != null) return down ? "held during capture" : "release deferred";
        if (!down) return releaseQuickFocus();
        if (quickFocus != null) return "held " + quickFocus;
        try {
            Camera.Parameters p = cam.getNormalCamera().getParameters();
            if (CameraEx.ParametersModifier.FOCUS_MODE_MANUAL.equals(p.getFocusMode())) return "manual focus";
            String mode = SonyValues.pick(cam.createParametersModifier(p).getSupportedQuickAutoFocus(),
                    new String[]{CameraEx.ParametersModifier.QUICK_AF_WI_AEL,
                            CameraEx.ParametersModifier.QUICK_AF_WO_AEL});
            if (mode == null) return "failed: native half-press AF unavailable";
            cam.setQuickAutoFocus(mode);
            quickFocus = mode;
            return "started " + mode;
        } catch (Throwable t) {
            return "failed: " + ApplyReport.describe(t);
        }
    }

    private String releaseQuickFocus() {
        String mode = quickFocus;
        if (mode == null) return "released";
        try {
            cam.resetQuickAutoFocus(mode);
            quickFocus = null;
            return "released " + mode;
        } catch (Throwable t) {
            return "failed: " + ApplyReport.describe(t);
        }
    }

    /** MENU, a new preset, or pause cancels both a countdown and a queued capture. */
    public void cancelPicture() {
        if (shot == null) return;
        cancelNativeShot();
        deliverShot(1, "cancelled");
    }

    /** Lets the shutter button shoot while the app is in front. */
    public void startDirectShutter() {
        if (selfTimer > 0) return;
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
        halfPressed = false;
        cancelPicture();
        releaseQuickFocus();
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
            report.addWritten(key, value, value);
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
        setPlain(report, key, detail, null, w);
    }

    /** {@code expected}: what reading the setting back should give, or null not to check. */
    private void setPlain(ApplyReport report, String key, String detail, String expected, Write w) {
        try {
            write(w);
            report.addWritten(key, detail, expected);
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
        halfPressed = false;
        cancelPicture();
        releaseQuickFocus();
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

        setPlain(report, "iso", r.isoAuto ? "auto" : Integer.toString(r.iso), Integer.toString(r.isoAuto ? 0 : r.iso), new Write() {
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

        if (r.minShutter != null) {
            applyMinShutter(report, cur, r.minShutter);
        } else if (r.exposureMode == Preset.ExposureMode.SHUTTER_PRIORITY || r.exposureMode == Preset.ExposureMode.MANUAL) {
            // The Auto ISO low limit also caps the shutter in S and M on an A5100: a 1/100 left by an
            // earlier preset stopped 1/60 at 1/100. BetterManual clears it the same way.
            setPlain(report, "min_shutter", "cleared", "-1", new Write() {
                public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { m.setAutoShutterSpeedLowLimit(-1); }
            });
        }

        try {
            final int idx = SonyValues.evIndex(r.ev, now.getExposureCompensationStep(),
                    now.getMinExposureCompensation(), now.getMaxExposureCompensation());
            setPlain(report, "ev", Scales.formatEv(r.ev), Integer.toString(idx), new Write() {
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

        readBack(report, af(r));

        // Direct shutter owns the physical button and bypasses the timed capture entry point.
        // Stop it before the next full press; MainActivity handles that press in timer mode.
        selfTimer = r.selfTimer;
        timerReady = false;
        try {
            if (selfTimer > 0) {
                cam.stopDirectShutter(new CameraEx.DirectShutterStoppedCallback() {
                    @Override
                    public void onShutterStopped(CameraEx c) {
                        if (!closed && generation == applyGeneration) timerReady = true;
                    }
                });
            } else {
                cam.startDirectShutter();
            }
        } catch (Throwable t) {
            report.add("self_timer", ApplyReport.Status.FAILED, ApplyReport.describe(t));
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

    private static boolean af(Recommendation r) {
        return SonyValues.autoFocusMode(r.focusMode) != null;
    }

    /** Reads every written setting back (see ApplyReport.readBack); a getter that throws is skipped. */
    private void readBack(final ApplyReport report, final boolean autoFocus) {
        final Camera.Parameters p;
        final CameraEx.ParametersModifier m;
        try {
            p = cam.getNormalCamera().getParameters();
            m = cam.createParametersModifier(p);
        } catch (Throwable t) {
            return;
        }
        check(report, "exposure_mode", new ProbeReport.Read() { public Object get() { return p.getSceneMode(); } });
        check(report, "iso", new ProbeReport.Read() { public Object get() { return m.getISOSensitivity(); } });
        check(report, "ev", new ProbeReport.Read() { public Object get() { return p.getExposureCompensation(); } });
        check(report, "min_shutter", new ProbeReport.Read() { public Object get() { return m.getAutoShutterSpeedLowLimit(); } });
        check(report, "metering", new ProbeReport.Read() { public Object get() { return m.getMeteringMode(); } });
        check(report, "focus_mode", new ProbeReport.Read() {
            public Object get() { return autoFocus ? m.getAutoFocusMode() : p.getFocusMode(); }
        });
        check(report, "focus_area", new ProbeReport.Read() { public Object get() { return m.getFocusAreaMode(); } });
        check(report, "face", new ProbeReport.Read() { public Object get() { return m.getFaceDetectionMode(); } });
        check(report, "drive", new ProbeReport.Read() { public Object get() { return m.getDriveMode(); } });
        check(report, "self_timer", new ProbeReport.Read() { public Object get() { return m.getSelfTimer(); } });
        check(report, "creative_style", new ProbeReport.Read() { public Object get() { return m.getColorMode(); } });
        check(report, "soft_skin", new ProbeReport.Read() { public Object get() { return m.getSoftSkinEffect(); } });
    }

    private static void check(ApplyReport report, String key, ProbeReport.Read read) {
        try {
            report.readBack(key, String.valueOf(read.get()));
        } catch (Throwable ignored) {
            // No getter on this firmware: the setting stays "OK" without a read-back.
        }
    }

    /**
     * Focus area: each supported name in turn, kept only if the camera reads it back. The A5100 lists
     * "wide" yet keeps "center" after it, so the next candidate gets its chance.
     */
    private void applyFocusArea(ApplyReport report, final CameraEx.ParametersModifier cur, String area) {
        List<String> tries = SonyValues.supportedCandidates(supported(new SupportedList() {
            public List<?> get() { return cur.getSupportedFocusAreaModes(); }
        }), SonyValues.focusAreaCandidates(area));
        if (tries.isEmpty()) {
            report.add("focus_area", ApplyReport.Status.UNSUPPORTED, area + " not supported");
            return;
        }
        StringBuilder tried = new StringBuilder();
        for (final String v : tries) {
            String actual;
            try {
                write(new Write() {
                    public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { m.setFocusAreaMode(v); }
                });
                actual = current().getFocusAreaMode();
            } catch (Throwable t) {
                actual = ApplyReport.describe(t);
            }
            if (v.equals(actual)) {
                report.add("focus_area", ApplyReport.Status.OK, v + (tried.length() > 0 ? " (after " + tried + ")" : "") + ", read back");
                return;
            }
            if (tried.length() > 0) tried.append(", ");
            tried.append(v).append(" read back ").append(actual);
        }
        report.add("focus_area", ApplyReport.Status.MISMATCH, area + ": " + tried);
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
        setPlain(report, "min_shutter", min.format() + " (code " + code + ")", Integer.toString(code), new Write() {
            public void to(Camera.Parameters p, CameraEx.ParametersModifier m) { m.setAutoShutterSpeedLowLimit(code); }
        });
    }

    private void applyFocus(ApplyReport report, Camera.Parameters now, final CameraEx.ParametersModifier cur,
                            final Recommendation r) {
        final String af = SonyValues.autoFocusMode(r.focusMode);
        if (af == null) {
            setPlain(report, "focus_mode", "manual", CameraEx.ParametersModifier.FOCUS_MODE_MANUAL, new Write() {
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
        if (r.focusArea != null) applyFocusArea(report, cur, r.focusArea);
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
        out.probe("autoExposureLock", new ProbeReport.Read() { public Object get() { return m.getAutoExposureLock(); } });
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
        out.probe("autoExposureLocks", new ProbeReport.Read() { public Object get() { return m.getSupportedAutoExposureLocks(); } });
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
