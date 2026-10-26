package com.habitflow.service;

import com.habitflow.dto.HabitRequest;
import com.habitflow.dto.HabitStatsResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.NotFoundException;
import com.habitflow.model.Frequency;
import com.habitflow.model.Habit;
import com.habitflow.repository.CategoryRepository;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final CategoryRepository categoryRepository;
    private final CompletionRepository completionRepository;
    private final StreakCalculator streaks;

    public HabitService(HabitRepository habitRepository, CategoryRepository categoryRepository,
                        CompletionRepository completionRepository, StreakCalculator streaks) {
        this.habitRepository = habitRepository;
        this.categoryRepository = categoryRepository;
        this.completionRepository = completionRepository;
        this.streaks = streaks;
    }

    public List<Habit> list(Long userId, String status) {
        return habitRepository.findByUser(userId, status);
    }

    public Habit getOwned(Long habitId, Long userId) {
        return habitRepository.findByIdAndUser(habitId, userId)
                .orElseThrow(() -> new NotFoundException("Habit not found."));
    }

    @Transactional
    public Habit create(Long userId, HabitRequest request) {
        validateCategory(request.categoryId(), userId);
        String frequency = request.frequency().toUpperCase();
        Set<Integer> days = resolveSchedule(frequency, request.scheduleDays(), request.weeklyTarget());
        LocalDate start = request.startDate() == null ? LocalDate.now() : request.startDate();
        if (start.isAfter(LocalDate.now().plusYears(1))) {
            throw new BadRequestException("Start date cannot be more than a year in the future.");
        }
        Long id = habitRepository.insert(userId, request.categoryId(), request.name().trim(),
                trimToNull(request.description()), request.targetValue(), trimToNull(request.targetUnit()),
                frequency, request.weeklyTarget(),
                defaultIfBlank(request.color(), "#7C6BFF"),
                defaultIfBlank(request.icon(), "bi-check2-circle"),
                parseTime(request.reminderTime()), start,
                request.status() == null ? "ACTIVE" : request.status().toUpperCase());
        habitRepository.replaceSchedule(id, days);
        return getOwned(id, userId);
    }

    @Transactional
    public Habit update(Long userId, Long habitId, HabitRequest request) {
        Habit existing = getOwned(habitId, userId);
        validateCategory(request.categoryId(), userId);
        String frequency = request.frequency().toUpperCase();
        Set<Integer> days = resolveSchedule(frequency, request.scheduleDays(), request.weeklyTarget());
        habitRepository.update(habitId, userId, request.categoryId(), request.name().trim(),
                trimToNull(request.description()), request.targetValue(), trimToNull(request.targetUnit()),
                frequency, request.weeklyTarget(),
                defaultIfBlank(request.color(), existing.color()),
                defaultIfBlank(request.icon(), existing.icon()),
                parseTime(request.reminderTime()),
                request.startDate() == null ? existing.startDate() : request.startDate(),
                request.status() == null ? existing.status() : request.status().toUpperCase());
        habitRepository.replaceSchedule(habitId, days);
        return getOwned(habitId, userId);
    }

    @Transactional
    public Habit setStatus(Long userId, Long habitId, String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"ARCHIVED".equalsIgnoreCase(status)) {
            throw new BadRequestException("Status must be ACTIVE or ARCHIVED.");
        }
        getOwned(habitId, userId);
        habitRepository.updateStatus(habitId, userId, status.toUpperCase());
        return getOwned(habitId, userId);
    }

    @Transactional
    public void delete(Long userId, Long habitId) {
        getOwned(habitId, userId);
        // habit_schedules / habit_completions / goal_habits cascade on delete
        habitRepository.delete(habitId, userId);
    }

    public HabitStatsResponse stats(Long userId, Long habitId, LocalDate from, LocalDate to) {
        Habit habit = getOwned(habitId, userId);
        Set<LocalDate> dates = completionRepository.datesForHabit(habitId, userId);
        LocalDate today = LocalDate.now();
        int current = streaks.currentStreak(habit, dates, today);
        int longest = streaks.longestStreak(habit, dates, today);

        LocalDate weekFrom = today.minusDays(6);
        LocalDate monthFrom = today.minusDays(29);
        int weeklyScheduled = streaks.scheduledCount(habit, weekFrom, today);
        int weeklyDone = streaks.completedScheduledCount(habit, dates, weekFrom, today);
        int monthlyScheduled = streaks.scheduledCount(habit, monthFrom, today);
        int monthlyDone = streaks.completedScheduledCount(habit, dates, monthFrom, today);
        LocalDate rangeFrom = from == null ? habit.startDate() : from;
        LocalDate rangeTo = to == null ? today : to;
        int rangeScheduled = streaks.scheduledCount(habit, rangeFrom, rangeTo);
        int rangeDone = streaks.completedScheduledCount(habit, dates, rangeFrom, rangeTo);
        double overall = Numbers.percent(rangeDone, rangeScheduled);

        return new HabitStatsResponse(habit.id(), habit.name(), dates.size(), current, longest,
                Numbers.percent(weeklyDone, weeklyScheduled), Numbers.percent(monthlyDone, monthlyScheduled),
                overall, completionRepository.lastCompletionDate(habitId, userId).orElse(null),
                rangeScheduled, rangeDone, consistencyLabel(overall));
    }

    private String consistencyLabel(double percent) {
        if (percent >= 85) {
            return "Excellent";
        }
        if (percent >= 65) {
            return "Good";
        }
        if (percent >= 40) {
            return "Improving";
        }
        return "Needs attention";
    }

    private void validateCategory(Long categoryId, Long userId) {
        if (categoryId == null || !categoryRepository.isVisibleTo(categoryId, userId)) {
            throw new BadRequestException("Please choose a valid category.");
        }
    }

    /**
     * Turns frequency + optional explicit day list into the stored schedule.
     * WEEKLY means "any N days per week" - we store all 7 days and record the weekly target,
     * so the habit shows up every day but is only expected N times.
     */
    private Set<Integer> resolveSchedule(String frequency, List<Integer> requested, Integer weeklyTarget) {
        if (requested != null && !requested.isEmpty()) {
            Set<Integer> days = new LinkedHashSet<>();
            for (Integer d : requested) {
                if (d != null && d >= 1 && d <= 7) {
                    days.add(d);
                }
            }
            if (!days.isEmpty()) {
                return days;
            }
        }
        Set<Integer> defaults = Frequency.defaultDays(frequency);
        if (defaults.isEmpty()) {
            throw new BadRequestException("Please choose at least one day for a custom schedule.");
        }
        if ("WEEKLY".equals(frequency) && weeklyTarget == null) {
            throw new BadRequestException("A weekly habit needs a weekly target between 1 and 7.");
        }
        return defaults;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Reminder time must look like 07:30.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
