package com.lenspreset.core.optics;

/**
 * Thin-lens optics, in millimetres unless a name says otherwise. Good to a few percent at portrait
 * distances, which is finer than anyone can stand on a mark.
 */
public final class Optics {
    private Optics() {
    }

    public static final double FULL_FRAME_DIAGONAL_MM = 43.27;

    public static double cropFactor(double sensorDiagonalMm) {
        return FULL_FRAME_DIAGONAL_MM / sensorDiagonalMm;
    }

    /** The reciprocal rule with a 2x margin for today's pixel densities: 1 / (2 x focal x crop). */
    public static double handheldSeconds(double focalMm, double crop, double stabilizationStops) {
        return Math.pow(2, stabilizationStops) / (2 * focalMm * crop);
    }

    /**
     * Subject distance (metres) at which {@code extentM} of the scene fills {@code sensorDimMm} of
     * the sensor: s = f (1 + E / h).
     */
    public static double distanceForExtentM(double extentM, double focalMm, double sensorDimMm) {
        return focalMm * (1 + extentM * 1000 / sensorDimMm) / 1000;
    }

    /** Field of view across {@code sensorDimMm}, in degrees, focused at infinity. */
    public static double fieldOfViewDeg(double focalMm, double sensorDimMm) {
        return Math.toDegrees(2 * Math.atan(sensorDimMm / (2 * focalMm)));
    }

    public static double hyperfocalMm(double focalMm, double fNumber, double cocMm) {
        return focalMm * focalMm / (fNumber * cocMm) + focalMm;
    }

    /** Near limit of acceptable sharpness for focus at {@code distanceM}, in metres. */
    public static double dofNearM(double focalMm, double fNumber, double cocMm, double distanceM) {
        double h = hyperfocalMm(focalMm, fNumber, cocMm);
        double s = distanceM * 1000;
        return s * (h - focalMm) / (h + s - 2 * focalMm) / 1000;
    }

    /** Far limit in metres; {@link Double#POSITIVE_INFINITY} at or beyond the hyperfocal distance. */
    public static double dofFarM(double focalMm, double fNumber, double cocMm, double distanceM) {
        double h = hyperfocalMm(focalMm, fNumber, cocMm);
        double s = distanceM * 1000;
        if (s >= h) return Double.POSITIVE_INFINITY;
        return s * (h - focalMm) / (h - s) / 1000;
    }
}
