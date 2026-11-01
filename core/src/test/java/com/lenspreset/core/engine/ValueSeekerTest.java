package com.lenspreset.core.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueSeekerTest {
    /** A dial with third-stop clicks between {@code min} and {@code max}; {@code sign} says what INCREMENT does. */
    static final class FakeDial {
        double value;
        final double min;
        final double max;
        final int sign;

        FakeDial(double value, double min, double max, int sign) {
            this.value = value;
            this.min = min;
            this.max = max;
            this.sign = sign;
        }

        void apply(int command) {
            double next = value + command * sign / 3.0;
            if (next >= min - 1e-9 && next <= max + 1e-9) value = next;
        }
    }

    static ValueSeeker.Outcome lastOutcome;

    static int seek(FakeDial dial, double target) {
        ValueSeeker s = new ValueSeeker(target, 0.17, 40);
        int cmd;
        int n = 0;
        while ((cmd = s.next(dial.value)) != ValueSeeker.DONE) {
            dial.apply(cmd);
            n++;
        }
        lastOutcome = s.outcome();
        return n;
    }

    @Test
    void reachesTheTargetWhenIncrementRaises() {
        FakeDial d = new FakeDial(1.0, 0, 8, 1);
        assertEquals(6, seek(d, 3.0));
        assertEquals(3.0, d.value, 1e-9);
        assertEquals(ValueSeeker.Outcome.REACHED, lastOutcome);
    }

    @Test
    void learnsThatIncrementLowers() {
        FakeDial d = new FakeDial(1.0, 0, 8, -1);
        seek(d, 3.0);
        assertEquals(3.0, d.value, 1e-9);
    }

    @Test
    void stopsAtTheLimit() {
        FakeDial d = new FakeDial(1.0, 0, 2, 1);
        seek(d, 5.0);
        assertEquals(2.0, d.value, 1e-9);
        assertEquals(ValueSeeker.Outcome.LIMIT, lastOutcome);
    }

    @Test
    void aKnownDirectionSavesTheFirstStep() {
        FakeDial guessed = new FakeDial(1.0, 0, 8, -1);
        int withGuess = seek(guessed, 3.0);
        ValueSeeker s = new ValueSeeker(3.0, 0.17, 40, ValueSeeker.DECREMENT);
        FakeDial known = new FakeDial(1.0, 0, 8, -1);
        int n = 0;
        int cmd;
        while ((cmd = s.next(known.value)) != ValueSeeker.DONE) {
            known.apply(cmd);
            n++;
        }
        assertEquals(3.0, known.value, 1e-9);
        assertEquals(6, n);
        assertTrue(withGuess > n, withGuess + " vs " + n);
    }

    @Test
    void triesTheOtherWayWhenTheFirstGuessHitsALimit() {
        // At the bottom already and INCREMENT actually lowers: the first guess does nothing.
        FakeDial d = new FakeDial(0, 0, 8, -1);
        seek(d, 2.0);
        assertEquals(2.0, d.value, 1e-9);
    }

    @Test
    void doneImmediatelyWhenAlreadyThere() {
        FakeDial d = new FakeDial(2.0, 0, 8, 1);
        assertEquals(0, seek(d, 2.1));
    }

    @Test
    void stepsBackAfterOvershootingWhenThePreviousValueWasCloser() {
        // Half-stop clicks: from 0 toward 0.6 the dial goes 0, 0.5, 1.0; 0.5 is closer.
        ValueSeeker s = new ValueSeeker(0.6, 0.05, 10);
        assertEquals(ValueSeeker.INCREMENT, s.next(0));
        assertEquals(ValueSeeker.INCREMENT, s.next(0.5));
        assertEquals(ValueSeeker.DECREMENT, s.next(1.0));
        assertEquals(ValueSeeker.DONE, s.next(0.5));
        assertTrue(s.isFinished());
        assertEquals(ValueSeeker.Outcome.NEAREST, s.outcome());
    }

    @Test
    void settlesOnTheCloserOfTwoNeighboursAsTheA5100Does() {
        // Sigma 30 on an A5100 reports 2.8 2.5 2.2 2.0 1.7 (f-numbers): no 1.8 to stop on.
        double[] fNumbers = {2.8, 2.5, 2.2, 2.0, 1.7, 1.4};
        ValueSeeker s = new ValueSeeker(stops(1.8), 0.12, 40);
        int i = 0;
        int cmd;
        while ((cmd = s.next(stops(fNumbers[i]))) != ValueSeeker.DONE) {
            i += cmd == ValueSeeker.INCREMENT ? 1 : -1;
        }
        assertEquals(1.7, fNumbers[i]);
        assertEquals(ValueSeeker.Outcome.NEAREST, s.outcome());
    }

    private static double stops(double fNumber) {
        // Opening the aperture is INCREMENT here; the seeker learns that from the first step.
        return -2 * Math.log(fNumber) / Math.log(2);
    }

    @Test
    void respectsTheStepBudget() {
        ValueSeeker s = new ValueSeeker(100, 0.1, 3);
        double v = 0;
        int n = 0;
        while (s.next(v) != ValueSeeker.DONE) {
            v += 1;
            n++;
        }
        assertEquals(3, n);
        assertEquals(ValueSeeker.Outcome.BUDGET, s.outcome());
    }
}
