package com.lenspreset.core.engine;

import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.Feature;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Optics;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.optics.Shutter;

import java.util.ArrayList;
import java.util.List;

/**
 * camera + lens + preset (+ what the camera reports now) to settings. Pure: no Android, no
 * camera, so every combination of the bundled profiles is checked on the JVM.
 *
 * <p>Every value is clamped to what the lens and body can do. A feature the camera profile marks
 * {@code no} is dropped with a notice; {@code unknown} is still tried, since only a real body can
 * tell, and the adapter reports a refusal.
 */
public final class PresetEngine {
    private PresetEngine() {
    }

    /** Scene height (or group width) a framing fills, in metres. */
    static double framingExtentM(Preset.Subject subject) {
        if (Preset.Subject.HEAD_SHOULDERS.equals(subject.framing)) return 0.55;
        if (Preset.Subject.HALF_BODY.equals(subject.framing)) return 1.0;
        if (Preset.Subject.FULL_BODY.equals(subject.framing)) return 2.1;
        if (Preset.Subject.ENVIRONMENT.equals(subject.framing)) return 4.0;
        // A group: shoulder width per person plus a margin either side.
        return subject.peoplePerRow * 0.55 + 1.0;
    }

    public static Recommendation compute(CameraProfile camera, LensProfile lens, Preset preset, Conditions now) {
        List<Message> hints = new ArrayList<Message>();
        List<Message> notices = new ArrayList<Message>();

        if (camera.status == CameraProfile.Status.UNTESTED)
            notices.add(Message.of(Message.Level.INFO, "notice.untested_camera", "name", camera.name));

        // Image format: an APS-C lens on a full-frame body makes the body crop to APS-C.
        double sensorW = camera.sensor.widthMm;
        double sensorH = camera.sensor.heightMm;
        double coc = camera.sensor.cocMm;
        if (LensProfile.Coverage.APS_C.equals(lens.coverage) && "full-frame".equals(camera.sensor.format)) {
            sensorW /= 1.5;
            sensorH /= 1.5;
            coc /= 1.5;
            notices.add(Message.of(Message.Level.INFO, "notice.crop_mode"));
        }
        double crop = Optics.cropFactor(Math.sqrt(sensorW * sensorW + sensorH * sensorH));

        double focal;
        if (!lens.zoom) {
            focal = lens.focalMin;
        } else if (now.focalMm != null) {
            focal = lens.clampFocal(now.focalMm);
        } else {
            focal = lens.focalMin;
            notices.add(Message.of(Message.Level.INFO, "notice.focal_assumed", "mm", fmtMm(focal)));
        }

        double widest = lens.maxApertureAt(focal);
        if (now.liveMaxAperture != null && lens.electronic
                && now.liveMaxAperture >= lens.apertureMax - 0.05 && now.liveMaxAperture < lens.apertureMin) {
            widest = now.liveMaxAperture;
        }
        widest = Scales.apertureAtLeast(widest);

        // Subject distance for the framing.
        int rows = override(preset, now, Preset.Adjust.ROWS, 1);
        Double distance = null;
        if (preset.subject != null) {
            boolean across = Preset.Subject.GROUP.equals(preset.subject.framing);
            distance = Optics.distanceForExtentM(framingExtentM(preset.subject), focal, across ? sensorW : sensorH);
            if (distance < lens.minFocusM) {
                notices.add(Message.of(Message.Level.WARN, "notice.distance_too_close", "m", Scales.formatMetres(lens.minFocusM)));
                distance = lens.minFocusM;
            }
        }

        // Aperture.
        Double aperture = null;
        Double dofNear = null;
        Double dofFar = null;
        if (preset.aperture != null) {
            Preset.ApertureRule r = preset.aperture;
            double n;
            if (Preset.ApertureRule.WIDEST_SAFE.equals(r.strategy)) {
                n = Scales.fromApertureStops(Scales.apertureStops(widest) + r.stopDownFromMax);
            } else if (Preset.ApertureRule.SWEET_SPOT.equals(r.strategy)) {
                double lo = lens.sweetSpot != null ? lens.sweetSpot[0] : Scales.fromApertureStops(Scales.apertureStops(widest) + 2);
                double hi = lens.sweetSpot != null ? lens.sweetSpot[1] : Math.max(lo, 8.0);
                lo = Math.max(lo, widest);
                hi = Math.max(hi, lo);
                if ("high".equals(r.position)) n = hi;
                else if ("mid".equals(r.position)) n = Math.sqrt(lo * hi);
                else n = lo;
            } else if (Preset.ApertureRule.DOF.equals(r.strategy)) {
                double depth = r.depthM + r.perRowM * (rows - 1);
                n = apertureForDepth(focal, coc, distance, depth, widest, lens.apertureMin);
                if (n < 0) {
                    n = lens.apertureMin;
                    notices.add(Message.of(Message.Level.WARN, "notice.dof_insufficient"));
                }
            } else if (Preset.ApertureRule.FIXED.equals(r.strategy)) {
                n = r.value;
            } else {
                n = widest;
            }
            if (r.minF != null) n = Math.max(n, r.minF);
            if (r.maxF != null) n = Math.min(n, r.maxF);
            n = Math.max(widest, Math.min(lens.apertureMin, n));
            aperture = Scales.apertureAtLeast(n);
            if (aperture > lens.apertureMin) aperture = Scales.apertureAtMost(lens.apertureMin);

            if (distance != null) {
                dofNear = Optics.dofNearM(focal, aperture, coc, distance);
                dofFar = Optics.dofFarM(focal, aperture, coc, distance);
            }
            if (!lens.electronic)
                hints.add(Message.of(Message.Level.HINT, "hint.set_aperture_ring", "f", Scales.formatAperture(aperture)));
        }

        // Shutter.
        Shutter shutter = null;
        Shutter minShutter = null;
        if (preset.shutter != null) {
            Preset.ShutterRule r = preset.shutter;
            Shutter s;
            if (Preset.ShutterRule.FIXED.equals(r.strategy)) {
                s = r.value;
            } else {
                double stab = (lens.ois || camera.ibis) ? r.stabilizationStops : 0;
                double sec = Optics.handheldSeconds(focal, crop, stab);
                if (r.min != null) sec = Math.min(sec, r.min.seconds());
                s = Scales.shutterAtLeastAsFast(sec);
            }
            boolean exact = preset.exposureMode == Preset.ExposureMode.SHUTTER_PRIORITY
                    || preset.exposureMode == Preset.ExposureMode.MANUAL;
            if (exact) {
                shutter = s;
            } else {
                minShutter = s;
                if (camera.feature(Feature.AUTO_ISO_MIN_SHUTTER) == Feature.NO)
                    notices.add(Message.of(Message.Level.WARN, "notice.min_shutter_watch", "shutter", s.format()));
            }
        }

        // ISO.
        boolean isoAuto = preset.iso.auto;
        int iso = 0;
        int isoMin = camera.isoMin;
        int isoMax = camera.autoIsoCeiling();
        if (isoAuto) {
            if (preset.iso.min != null) isoMin = Math.max(camera.isoMin, Math.min(isoMax, preset.iso.min));
            if (preset.iso.max != null) {
                if (preset.iso.max > isoMax)
                    notices.add(Message.of(Message.Level.INFO, "notice.iso_capped", "iso", Integer.toString(isoMax)));
                isoMax = Math.max(isoMin, Math.min(isoMax, preset.iso.max));
            }
        } else {
            iso = Math.max(camera.isoMin, Math.min(camera.isoMax, preset.iso.value));
        }
        boolean isoRangeSettable = camera.feature(Feature.AUTO_ISO_RANGE) != Feature.NO;
        if (isoAuto && !isoRangeSettable && (preset.iso.max != null || preset.iso.min != null))
            notices.add(Message.of(Message.Level.INFO, "notice.iso_range_unavailable"));

        double ev = Scales.snapEv(Math.max(-camera.evRange, Math.min(camera.evRange, preset.ev)));

        String metering = preset.metering;
        if (camera.feature(Feature.METERING) == Feature.NO && !"multi".equals(metering)) {
            notices.add(Message.of(Message.Level.WARN, "notice.metering_unavailable"));
            metering = null;
        }

        // Focus.
        String focusMode = preset.af.mode;
        String focusArea = preset.af.area;
        boolean face = preset.af.face;
        boolean eye = false;
        if (!lens.af) {
            focusMode = Preset.AfRule.MANUAL;
            focusArea = null;
            face = false;
            hints.add(distance != null
                    ? Message.of(Message.Level.HINT, "hint.manual_focus_distance", "m", Scales.formatMetres(distance))
                    : Message.of(Message.Level.HINT, "hint.manual_focus"));
        } else {
            if (Preset.AfRule.CONTINUOUS.equals(focusMode) && camera.feature(Feature.CONTINUOUS_AF) == Feature.NO) {
                focusMode = Preset.AfRule.SINGLE;
                notices.add(Message.of(Message.Level.WARN, "notice.continuous_af_unavailable"));
            }
            if (face && camera.feature(Feature.FACE_DETECTION) == Feature.NO) {
                face = false;
                notices.add(Message.of(Message.Level.WARN, "notice.face_unavailable"));
            }
            if ("if_available".equals(preset.af.eye)) {
                if (camera.feature(Feature.EYE_AF) == Feature.YES) eye = true;
                else hints.add(Message.of(Message.Level.HINT, "hint.eye_af_button"));
            }
        }

        // Drive.
        String drive = preset.drive;
        if (!"single".equals(drive) && camera.feature(Feature.BURST) == Feature.NO) {
            drive = "single";
            notices.add(Message.of(Message.Level.WARN, "notice.burst_unavailable"));
        }
        int selfTimer = override(preset, now, Preset.Adjust.SELF_TIMER, preset.selfTimer);
        if (selfTimer > 0 && camera.feature(Feature.SELF_TIMER) == Feature.NO) {
            selfTimer = 0;
            notices.add(Message.of(Message.Level.WARN, "notice.self_timer_unavailable"));
        }
        // Timed continuous shooting is a separate Sony mode, not burst plus a delay.
        if (selfTimer > 0) drive = "single";

        // Look.
        String style = null;
        String styleFallback = null;
        String softSkin = null;
        if (preset.style != null) {
            style = preset.style.creativeStyle;
            styleFallback = preset.style.fallback;
            if (style != null && camera.feature(Feature.CREATIVE_STYLE) == Feature.NO) {
                notices.add(Message.of(Message.Level.INFO, "notice.style_unavailable"));
                style = null;
                styleFallback = null;
            }
            softSkin = preset.style.softSkin;
            if (softSkin != null && !"off".equals(softSkin) && camera.feature(Feature.SOFT_SKIN) == Feature.NO) {
                notices.add(Message.of(Message.Level.INFO, "notice.soft_skin_unavailable"));
                softSkin = null;
            }
        }

        // What to tell the photographer, most useful first.
        List<Message> messages = new ArrayList<Message>();
        if (distance != null && lens.af) {
            messages.add(Message.of(Message.Level.HINT, "hint.distance." + preset.subject.framing,
                    "m", Scales.formatMetres(distance)));
        }
        if (dofNear != null) {
            messages.add(Message.of(Message.Level.HINT, "hint.dof",
                    "near", Scales.formatMetres(dofNear), "far", Scales.formatMetres(dofFar)));
        }
        messages.addAll(hints);
        for (String h : preset.hints) messages.add(Message.of(Message.Level.HINT, "hint." + h));
        messages.addAll(notices);

        return new Recommendation(preset.exposureMode, aperture, lens.electronic, shutter, minShutter,
                isoAuto, iso, isoAuto ? isoMin : 0, isoAuto ? isoMax : 0, isoAuto && isoRangeSettable, ev, metering, focusMode,
                focusArea, face, eye, drive, selfTimer, style, styleFallback, softSkin,
                preset.overlays, messages, focal, focal * crop, distance, dofNear, dofFar);
    }

    /**
     * The widest marked f-number whose depth of field covers {@code depthM} centred on the focus
     * distance, or -1 if even {@code narrowest} does not.
     */
    static double apertureForDepth(double focal, double coc, double distanceM, double depthM,
                                   double widest, double narrowest) {
        double near = distanceM - depthM / 2;
        double far = distanceM + depthM / 2;
        for (double a : Scales.APERTURES) {
            if (a < widest - 1e-6 || a > narrowest + 1e-6) continue;
            if (Optics.dofNearM(focal, a, coc, distanceM) <= near && Optics.dofFarM(focal, a, coc, distanceM) >= far)
                return a;
        }
        return -1;
    }

    private static int override(Preset preset, Conditions now, String key, int fallback) {
        Preset.Adjust adjust = preset.adjust.get(key);
        if (adjust == null) return fallback;
        Integer v = now.overrides.get(key);
        if (v != null && adjust.allows(v)) return v;
        return adjust.defaultValue;
    }

    private static String fmtMm(double mm) {
        return Long.toString(Math.round(mm));
    }
}
