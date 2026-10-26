package com.habitflow.service;

import com.habitflow.util.Numbers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Completion-rate maths - the numbers every dashboard tile depends on. */
class NumbersTest {

    @Test
    @DisplayName("percentage is rounded to one decimal place")
    void percentRoundsToOneDecimal() {
        assertEquals(66.7, Numbers.percent(2, 3), 0.001);
        assertEquals(100.0, Numbers.percent(5, 5), 0.001);
        assertEquals(0.0, Numbers.percent(0, 5), 0.001);
    }

    @Test
    @DisplayName("a zero denominator returns 0 instead of dividing by zero")
    void zeroTotalIsSafe() {
        assertEquals(0.0, Numbers.percent(0, 0), 0.001);
        assertEquals(0.0, Numbers.percent(3.0, 0.0), 0.001);
    }

    @Test
    @DisplayName("percentages are clamped into 0..100")
    void clampKeepsRange() {
        assertEquals(100, Numbers.clampPercent(140.0));
        assertEquals(0, Numbers.clampPercent(-12.0));
        assertEquals(42, Numbers.clampPercent(42.4));
    }
}
