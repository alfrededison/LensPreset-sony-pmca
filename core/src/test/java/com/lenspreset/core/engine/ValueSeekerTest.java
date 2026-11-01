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

    static int seek(FakeDial dial, double target) {
        ValueSeeker s = new ValueSeeker(target, 0.17, 40);
        int cmd;
        int n = 0;
        while ((cmd = s.next(dial.value)) != ValueSeeker.DONE) {
            dial.apply(cmd);
            n++;
        }
        return n;
    }

    @Test
    void reachesTheTargetWhenIncrementRaises() {
        FakeDial d = new FakeDial(1.0, 0, 8, 1);
        assertEquals(6, seek(d, 3.0));
        assertEquals(3.0, d.value, 1e-9);
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
    }
}
