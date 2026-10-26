package com.habitflow.service;

import com.habitflow.model.Habit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * Pure streak / consistency maths, deliberately free of database access so it can be
 * unit tested directly (see StreakCalculatorTest).
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>A day only counts if it is <b>scheduled</b> for that habit (weekday in the habit
 *       schedule and on/after the habit start date). Unscheduled days are skipped, never
 *       treated as a miss.</li>
 *   <li>A <b>current streak</b> ends at the first scheduled day that has no completion,
 *       looking backwards. If today is scheduled but not yet ticked, the streak is measured
 *       from yesterday - you are not punished before the day is over.</li>
 *   <li>The <b>longest streak</b> is the longest such run anywhere between the habit start
 *       date and today.</li>
 * </ul>
 */
@Component
public class StreakCalculator {

    public boolean isScheduled(Habit habit, LocalDate date) {
        if (habit.scheduleDays() == null || habit.scheduleDays().isEmpty()) {
            return false;
        }
        if (date.isBefore(habit.startDate())) {
            return false;
        }
        return habit.scheduleDays().contains(date.getDayOfWeek().getValue());
    }

    public int currentStreak(Habit habit, Set<LocalDate> completedDates, LocalDate today) {
        if (completedDates == null || completedDates.isEmpty() || habit.scheduleDays().isEmpty()) {
            return 0;
        }
        LocalDate cursor = today;
        // Today still open? Then yesterday is the reference point.
        if (isScheduled(habit, cursor) && !completedDates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (!cursor.isBefore(habit.startDate())) {
            if (isScheduled(habit, cursor)) {
                if (completedDates.contains(cursor)) {
                    streak++;
                } else {
                    break;
                }
            }
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public int longestStreak(Habit habit, Set<LocalDate> completedDates, LocalDate today) {
        if (completedDates == null || completedDates.isEmpty() || habit.scheduleDays().isEmpty()) {
            return 0;
        }
        int best = 0;
        int run = 0;
        LocalDate cursor = habit.startDate();
        while (!cursor.isAfter(today)) {
            if (isScheduled(habit, cursor)) {
                if (completedDates.contains(cursor)) {
                    run++;
                    best = Math.max(best, run);
                } else {
                    run = 0;
                }
            }
            cursor = cursor.plusDays(1);
        }
        return best;
    }

    /** How many times the habit was actually due inside the inclusive range. */
    public int scheduledCount(Habit habit, LocalDate from, LocalDate to) {
        if (habit.scheduleDays() == null || habit.scheduleDays().isEmpty()) {
            return 0;
        }
        int count = 0;
        LocalDate cursor = from.isBefore(habit.startDate()) ? habit.startDate() : from;
        while (!cursor.isAfter(to)) {
            if (isScheduled(habit, cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    /** Completions inside the range that fell on a scheduled day (the only fair measure). */
    public int completedScheduledCount(Habit habit, Set<LocalDate> completedDates,
                                       LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date : completedDates) {
            if (!date.isBefore(from) && !date.isAfter(to) && isScheduled(habit, date)) {
                count++;
            }
        }
        return count;
    }
}
