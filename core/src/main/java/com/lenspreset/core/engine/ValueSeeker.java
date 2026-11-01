package com.lenspreset.core.engine;

/**
 * Walks a camera setting that only moves in steps (aperture, shutter speed) toward a target.
 *
 * <p>CameraEx offers {@code incrementAperture()} / {@code decrementAperture()} and reports the new
 * value through a listener, but which direction "increment" goes has not been confirmed on every
 * body. So the seeker learns it from the first step that moves the value, and stops when the
 * value is within tolerance, has passed the target (stepping back if the previous value was
 * closer), stops moving (the lens or body limit), or the step budget runs out.
 *
 * <p>Values are in stops (see {@link com.lenspreset.core.optics.Scales#apertureStops} and
 * {@link com.lenspreset.core.optics.Shutter#stops}) so one third-stop click is about 0.33.
 */
public final class ValueSeeker {
    public static final int DONE = 0;
    public static final int INCREMENT = 1;
    public static final int DECREMENT = -1;

    private final double target;
    private final double tolerance;
    private final int maxSteps;

    private int steps;
    /** The command that raises the value: assumed INCREMENT until a step shows otherwise. */
    private int raises = INCREMENT;
    private boolean directionKnown;
    private boolean triedReverse;
    private boolean finished;
    private boolean steppingBack;
    private double last = Double.NaN;
    private int lastCommand = DONE;

    public ValueSeeker(double targetStops, double toleranceStops, int maxSteps) {
        this.target = targetStops;
        this.tolerance = toleranceStops;
        this.maxSteps = maxSteps;
    }

    /**
     * Call with the value the camera reports (or the same value again after a timeout without a
     * report). Returns the next command, or {@link #DONE}.
     */
    public int next(double current) {
        if (finished) return DONE;
        if (steppingBack) return finish();

        if (lastCommand != DONE) {
            double moved = current - last;
            if (Math.abs(moved) < 1e-6) {
                // No movement: a limit. If the direction was only a guess, the guess may have pushed
                // into the limit; try the other way once.
                if (!directionKnown && !triedReverse) {
                    triedReverse = true;
                    raises = -raises;
                } else {
                    return finish();
                }
            } else {
                raises = moved > 0 ? lastCommand : -lastCommand;
                directionKnown = true;
                boolean crossed = (last - target) * (current - target) < 0;
                if (crossed) {
                    if (Math.abs(last - target) < Math.abs(current - target) - 1e-6) {
                        steppingBack = true;
                        return issue(current, -lastCommand);
                    }
                    return finish();
                }
            }
        }

        if (Math.abs(current - target) <= tolerance) return finish();
        if (steps >= maxSteps) return finish();
        int wantUp = target > current ? 1 : -1;
        return issue(current, wantUp > 0 ? raises : -raises);
    }

    private int issue(double current, int command) {
        steps++;
        last = current;
        lastCommand = command;
        return command;
    }

    private int finish() {
        finished = true;
        return DONE;
    }

    public boolean isFinished() {
        return finished;
    }

    public int steps() {
        return steps;
    }
}
