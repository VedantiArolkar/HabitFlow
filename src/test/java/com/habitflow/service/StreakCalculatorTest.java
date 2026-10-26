package com.habitflow.service;

import com.habitflow.model.Habit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the streak engine - no Spring, no database.
 * These encode the rules documented in the README, so a regression shows up immediately.
 */
class StreakCalculatorTest {

    private final StreakCalculator calculator = new StreakCalculator();

    /** Mon 2026-10-05 as a convenient anchor (it is a Monday). */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private Habit habit(Set<Integer> scheduleDays, LocalDate startDate) {
        return new Habit(1L, 1L, 1L, "Health", "#14B8A6", "Drink water", null,
                null, null, "CUSTOM", null, "#14B8A6", "bi-droplet", null,
                startDate, "ACTIVE", new LinkedHashSet<>(scheduleDays), LocalDateTime.now());
    }

    private static Set<Integer> allDays() {
        return new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    @DisplayName("only scheduled weekdays count as scheduled")
    void scheduledOnlyOnChosenWeekdays() {
        Habit weekdays = habit(new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5)), MONDAY);

        assertTrue(calculator.isScheduled(weekdays, MONDAY));            // Monday
        assertTrue(calculator.isScheduled(weekdays, MONDAY.plusDays(4))); // Friday
        assertFalse(calculator.isScheduled(weekdays, MONDAY.plusDays(5))); // Saturday
    }

    @Test
    @DisplayName("days before the habit start date are never scheduled")
    void startDateGuardsSchedule() {
        Habit later = habit(allDays(), MONDAY.plusDays(10));

        assertFalse(calculator.isScheduled(later, MONDAY));
        assertTrue(calculator.isScheduled(later, MONDAY.plusDays(10)));
    }

    @Test
    @DisplayName("a run of consecutive scheduled completions is the current streak")
    void currentStreakCountsConsecutiveDays() {
        Habit daily = habit(allDays(), MONDAY);
        Set<LocalDate> done = Set.of(
                MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3));

        assertEquals(4, calculator.currentStreak(daily, done, MONDAY.plusDays(3)));
    }

    @Test
    @DisplayName("an unchecked scheduled day breaks the streak")
    void missingScheduledDayResetsStreak() {
        Habit daily = habit(allDays(), MONDAY);
        // Mon, Tue done, Wed missed, Thu done
        Set<LocalDate> done = Set.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(3));

        assertEquals(1, calculator.currentStreak(daily, done, MONDAY.plusDays(3)));
    }

    @Test
    @DisplayName("today being open does not break yesterday's streak")
    void openTodayKeepsStreakFromYesterday() {
        Habit daily = habit(allDays(), MONDAY);
        Set<LocalDate> done = Set.of(MONDAY, MONDAY.plusDays(1));

        // Today (Wed) is scheduled but not ticked yet - streak is still 2.
        assertEquals(2, calculator.currentStreak(daily, done, MONDAY.plusDays(2)));
    }

    @Test
    @DisplayName("unscheduled days are skipped, never treated as misses")
    void unscheduledDaysDoNotBreakStreak() {
        // Only Mon/Wed/Fri are scheduled. Complete Mon + Wed, skip Tue/Thu entirely.
        Habit monWedFri = habit(new LinkedHashSet<>(Set.of(1, 3, 5)), MONDAY);
        Set<LocalDate> done = Set.of(MONDAY, MONDAY.plusDays(2), MONDAY.plusDays(4));

        assertEquals(3, calculator.currentStreak(monWedFri, done, MONDAY.plusDays(4)));
    }

    @Test
    @DisplayName("longest streak is the best run anywhere in the history")
    void longestStreakFindsBestRun() {
        Habit daily = habit(allDays(), MONDAY);
        Set<LocalDate> done = Set.of(
                MONDAY, MONDAY.plusDays(1),                       // 2
                MONDAY.plusDays(3),                               // break, 1
                MONDAY.plusDays(5), MONDAY.plusDays(6), MONDAY.plusDays(7)); // 3

        assertEquals(3, calculator.longestStreak(daily, done, MONDAY.plusDays(7)));
        assertEquals(3, calculator.currentStreak(daily, done, MONDAY.plusDays(7)));
    }

    @Test
    @DisplayName("scheduled and completed-scheduled counts ignore unscheduled days")
    void scheduledCounts() {
        Habit weekdays = habit(new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5)), MONDAY);
        Set<LocalDate> done = Set.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(5)); // Mon, Tue, Sat

        // Mon..Sun = 5 scheduled weekdays
        assertEquals(5, calculator.scheduledCount(weekdays, MONDAY, MONDAY.plusDays(6)));
        // Saturday's completion is not on a scheduled day, so it must not count
        assertEquals(2, calculator.completedScheduledCount(weekdays, done, MONDAY, MONDAY.plusDays(6)));
    }

    @Test
    @DisplayName("a habit with no completions has a zero streak")
    void emptyHistoryIsZero() {
        Habit daily = habit(allDays(), MONDAY);
        assertEquals(0, calculator.currentStreak(daily, Set.of(), MONDAY));
        assertEquals(0, calculator.longestStreak(daily, Set.of(), MONDAY));
    }
}
