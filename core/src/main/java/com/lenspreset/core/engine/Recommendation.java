package com.lenspreset.core.engine;

import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Shutter;

import java.util.Collections;
import java.util.List;

/**
 * The settings a preset resolves to on one body and lens. A null field means "leave the camera's
 * own value". The camera adapter applies it; the overlay shows it.
 */
public final class Recommendation {
    public final Preset.ExposureMode exposureMode;

    /** Target f-number; null when the camera chooses (shutter priority, program). */
    public final Double aperture;
    /** False for manual lenses: the user turns the aperture ring to {@link #aperture}. */
    public final boolean apertureSettable;
    /** Exact shutter speed in shutter priority and manual; null otherwise. */
    public final Shutter shutter;
    /** Slowest speed Auto ISO may pick (aperture priority, program); null for none. */
    public final Shutter minShutter;

    public final boolean isoAuto;
    /** Fixed ISO; 0 with Auto ISO. */
    public final int iso;
    public final int isoAutoMin;
    public final int isoAutoMax;
    /** False when the body cannot limit Auto ISO: the range above is then not sent or shown. */
    public final boolean isoAutoRangeSettable;
    /** Exposure compensation in EV, on third stops. */
    public final double ev;

    /** {@code multi}, {@code center}, {@code spot}; null leaves it. */
    public final String metering;
    /** {@code single}, {@code continuous}, {@code automatic}, {@code manual}. */
    public final String focusMode;
    public final String focusArea;
    public final boolean faceDetection;
    public final boolean eyeAf;
    /** {@code single}, {@code burst_lo}, {@code burst_hi}. */
    public final String drive;
    public final int selfTimer;
    public final String creativeStyle;
    public final String creativeStyleFallback;
    public final String softSkin;

    public final List<String> overlays;
    /** Hints first, then notices: what to do, and what could not be done. */
    public final List<Message> messages;

    public final double focalMm;
    /** 35 mm equivalent focal length. */
    public final double equivalentFocalMm;
    /** Where to stand for the preset's framing; null when the preset has no subject. */
    public final Double subjectDistanceM;
    public final Double dofNearM;
    public final Double dofFarM;

    public Recommendation(Preset.ExposureMode exposureMode, Double aperture, boolean apertureSettable,
                          Shutter shutter, Shutter minShutter, boolean isoAuto, int iso, int isoAutoMin,
                          int isoAutoMax, boolean isoAutoRangeSettable, double ev, String metering, String focusMode, String focusArea,
                          boolean faceDetection, boolean eyeAf, String drive, int selfTimer,
                          String creativeStyle, String creativeStyleFallback, String softSkin,
                          List<String> overlays, List<Message> messages, double focalMm,
                          double equivalentFocalMm, Double subjectDistanceM, Double dofNearM,
                          Double dofFarM) {
        this.exposureMode = exposureMode;
        this.aperture = aperture;
        this.apertureSettable = apertureSettable;
        this.shutter = shutter;
        this.minShutter = minShutter;
        this.isoAuto = isoAuto;
        this.iso = iso;
        this.isoAutoMin = isoAutoMin;
        this.isoAutoMax = isoAutoMax;
        this.isoAutoRangeSettable = isoAutoRangeSettable;
        this.ev = ev;
        this.metering = metering;
        this.focusMode = focusMode;
        this.focusArea = focusArea;
        this.faceDetection = faceDetection;
        this.eyeAf = eyeAf;
        this.drive = drive;
        this.selfTimer = selfTimer;
        this.creativeStyle = creativeStyle;
        this.creativeStyleFallback = creativeStyleFallback;
        this.softSkin = softSkin;
        this.overlays = Collections.unmodifiableList(overlays);
        this.messages = Collections.unmodifiableList(messages);
        this.focalMm = focalMm;
        this.equivalentFocalMm = equivalentFocalMm;
        this.subjectDistanceM = subjectDistanceM;
        this.dofNearM = dofNearM;
        this.dofFarM = dofFarM;
    }

    public boolean hasMessage(String key) {
        for (Message m : messages) if (m.key.equals(key)) return true;
        return false;
    }
}
