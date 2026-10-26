package com.habitflow.service;

import com.habitflow.dto.CalendarDay;
import com.habitflow.dto.CompletionRequest;
import com.habitflow.dto.TodayHabit;
import com.habitflow.dto.TodayView;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.ConflictException;
import com.habitflow.exception.NotFoundException;
import com.habitflow.model.Habit;
import com.habitflow.model.HabitCompletion;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CompletionService {

    private final HabitRepository habitRepository;
    private final CompletionRepository completionRepository;
    private final StreakCalculator streaks;
    private final MetricsService metrics;
    private final NotificationService notificationService;
    private final GoalService goalService;

    public CompletionService(HabitRepository habitRepository, CompletionRepository completionRepository,
                             StreakCalculator streaks, MetricsService metrics,
                             NotificationService notificationService, GoalService goalService) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
        this.streaks = streaks;
        this.metrics = metrics;
        this.notificationService = notificationService;
        this.goalService = goalService;
    }

    /** Everything the tracking screen needs for one date. */
    public TodayView viewForDate(Long userId, LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date == null) {
            date = today;
        }
        List<Habit> habits = habitRepository.findByUser(userId, "ALL");
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, habits);
        Map<Long, HabitCompletion> completionsOnDate = new HashMap<>();
        for (HabitCompletion c : completionRepository.findByUserAndDate(userId, date)) {
            completionsOnDate.putIfAbsent(c.habitId(), c);
        }

        List<TodayHabit> rows = new ArrayList<>();
        int scheduled = 0;
        int completed = 0;
        int dueNow = 0;
        LocalTime now = LocalTime.now();

        for (Habit h : habits) {
            boolean scheduledToday = streaks.isScheduled(h, date);
            HabitCompletion completion = completionsOnDate.get(h.id());
            boolean done = completion != null;
            if (!scheduledToday && !done) {
                continue; // a habit that is not due is never shown as "pending"
            }
            if (!h.isActive() && !done) {
                continue;
            }
            if (scheduledToday) {
                scheduled++;
                if (done) {
                    completed++;
                }
            }
            if (!done && h.reminderTime() != null && date.equals(today)
                    && !h.reminderTime().isAfter(now)) {
                dueNow++;
            }
            int streak = streaks.currentStreak(h, metrics.datesFor(index, h.id()), today);
            rows.add(TodayHabit.of(h.id(), h.name(), h.icon(), h.color(), h.categoryName(),
                    h.frequency(), h.reminderTime() == null ? null : h.reminderTime().toString(),
                    scheduledToday, done, completion == null ? null : completion.note(), streak,
                    h.description()));
        }
        // pending first, then completed
        rows.sort((a, b) -> {
            if (a.completed() != b.completed()) {
                return a.completed() ? 1 : -1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });

        return new TodayView(date, date.equals(today), date.isAfter(today), scheduled, completed,
                dueNow, Numbers.percent(completed, scheduled), rows);
    }

    @Transactional
    public TodayHabit complete(Long userId, Long habitId, CompletionRequest request) {
        Habit habit = habitRepository.findByIdAndUser(habitId, userId)
                .orElseThrow(() -> new NotFoundException("Habit not found."));
        LocalDate date = request.date();
        boolean allowFuture = Boolean.TRUE.equals(request.allowFuture());
        validateCompletionAllowed(habit, date, allowFuture);

        if (completionRepository.exists(habitId, date)) {
            throw new ConflictException("This habit is already marked complete for " + date + ".");
        }
        try {
            completionRepository.insert(habitId, userId, date, trimToNull(request.note()));
        } catch (DuplicateKeyException ex) {
            // unique(habit_id, completion_date) caught a racing double submit
            throw new ConflictException("This habit is already marked complete for " + date + ".");
        }

        notificationService.onHabitCompleted(userId, habit, date);
        goalService.refreshMilestones(userId);

        List<Habit> habits = habitRepository.findByUser(userId, "ALL");
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, habits);
        int streak = streaks.currentStreak(habit, metrics.datesFor(index, habitId), LocalDate.now());
        return TodayHabit.of(habit.id(), habit.name(), habit.icon(), habit.color(), habit.categoryName(),
                habit.frequency(), habit.reminderTime() == null ? null : habit.reminderTime().toString(),
                true, true, trimToNull(request.note()), streak, habit.description());
    }

    @Transactional
    public void undo(Long userId, Long habitId, LocalDate date) {
        Habit habit = habitRepository.findByIdAndUser(habitId, userId)
                .orElseThrow(() -> new NotFoundException("Habit not found."));
        if (date == null) {
            date = LocalDate.now();
        }
        int deleted = completionRepository.delete(habitId, userId, date);
        if (deleted == 0) {
            throw new NotFoundException("No completion recorded for " + habit.name() + " on " + date + ".");
        }
    }

    public List<HabitCompletion> history(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must be before the end date.");
        }
        if (start.plusDays(400).isBefore(end)) {
            throw new BadRequestException("Please request a range of at most 400 days.");
        }
        return completionRepository.findByUserAndRange(userId, start, end);
    }

    public List<CalendarDay> calendar(Long userId, LocalDate from, LocalDate to, Long focusHabitId) {
        List<Habit> all = habitRepository.findByUser(userId, "ALL");
        List<Habit> habits = focusHabitId == null ? all
                : all.stream().filter(h -> h.id().equals(focusHabitId)).toList();
        if (focusHabitId != null && habits.isEmpty()) {
            throw new NotFoundException("Habit not found.");
        }
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, all);
        List<CalendarDay> days = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            MetricsService.DayStat stat = metrics.dayStat(habits, index, cursor);
            List<TodayHabit> rows = new ArrayList<>();
            for (Habit h : habits) {
                boolean scheduled = streaks.isScheduled(h, cursor);
                boolean done = metrics.datesFor(index, h.id()).contains(cursor);
                if (!scheduled && !done) {
                    continue;
                }
                rows.add(TodayHabit.of(h.id(), h.name(), h.icon(), h.color(), h.categoryName(),
                        h.frequency(), null, scheduled, done, null, 0, h.description()));
            }
            boolean complete = stat.scheduled() > 0 && stat.completed() >= stat.scheduled();
            days.add(new CalendarDay(cursor, stat.scheduled(), stat.completed(), complete, rows));
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    /** Server-side guard rails: no future completions (unless explicitly allowed), no pre-start dates. */
    private void validateCompletionAllowed(Habit habit, LocalDate date, boolean allowFuture) {
        if (date == null) {
            throw new BadRequestException("A date is required.");
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today) && !allowFuture) {
            throw new BadRequestException("You cannot mark a future date complete.");
        }
        if (date.isBefore(habit.startDate())) {
            throw new BadRequestException("This habit starts on " + habit.startDate() + ".");
        }
        if (!habit.isActive()) {
            throw new BadRequestException("This habit is archived. Reactivate it before tracking it.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
