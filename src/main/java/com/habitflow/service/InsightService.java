package com.habitflow.service;

import com.habitflow.dto.InsightResponse;
import com.habitflow.model.Habit;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based insights. Everything here is computed in Java from the user's own completion
 * history - no trained model and no external AI API. Each insight states the numbers it is
 * based on so the recommendation is explainable rather than a black box.
 */
@Service
public class InsightService {

    private final HabitRepository habitRepository;
    private final CompletionRepository completionRepository;
    private final MetricsService metrics;
    private final StreakCalculator streaks;

    public InsightService(HabitRepository habitRepository, CompletionRepository completionRepository,
                          MetricsService metrics, StreakCalculator streaks) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
        this.metrics = metrics;
        this.streaks = streaks;
    }

    public List<InsightResponse> generate(Long userId) {
        LocalDate today = LocalDate.now();
        List<Habit> all = habitRepository.findByUser(userId, "ALL");
        List<Habit> active = all.stream().filter(Habit::isActive).toList();
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, all);
        List<InsightResponse> insights = new ArrayList<>();

        if (active.isEmpty()) {
            insights.add(new InsightResponse("EMPTY", "No habits yet",
                    "Create your first habit and HabitFlow will start building your consistency profile.",
                    "bi-plus-circle", "info"));
            return insights;
        }

        LocalDate monthStart = today.minusDays(29);
        // 1. most consistent habit this month
        Habit bestHabit = null;
        double bestRate = -1;
        for (Habit h : active) {
            Set<LocalDate> dates = metrics.datesFor(index, h.id());
            int sched = streaks.scheduledCount(h, monthStart, today);
            if (sched == 0) {
                continue;
            }
            double rate = Numbers.percent(streaks.completedScheduledCount(h, dates, monthStart, today), sched);
            if (rate > bestRate) {
                bestRate = rate;
                bestHabit = h;
            }
        }
        if (bestHabit != null) {
            insights.add(new InsightResponse("TOP_HABIT", "Your most consistent habit",
                    "\"" + bestHabit.name() + "\" hit " + bestRate + "% of its scheduled days in the last 30 days. "
                            + "Whatever routine surrounds it is working - consider attaching a new habit to the same trigger.",
                    "bi-trophy", "success"));
        }

        // 2. frequently missed habits
        List<String> missed = new ArrayList<>();
        for (Habit h : active) {
            Set<LocalDate> dates = metrics.datesFor(index, h.id());
            int sched = streaks.scheduledCount(h, monthStart, today);
            if (sched < 5) {
                continue;
            }
            double rate = Numbers.percent(streaks.completedScheduledCount(h, dates, monthStart, today), sched);
            if (rate < 50) {
                missed.add(h.name() + " (" + rate + "%)");
            }
        }
        if (!missed.isEmpty()) {
            insights.add(new InsightResponse("MISSED_HABITS", "Habits you frequently miss",
                    String.join(", ", missed) + ". Try shrinking the habit to a two-minute version, "
                            + "or reschedule it to a day when you already have momentum.",
                    "bi-exclamation-triangle", "warning"));
        }

        // 3. best performing weekday & time of day
        Map<String, int[]> byWeekday = new LinkedHashMap<>();
        for (MetricsService.DayStat stat : metrics.range(active, index, today.minusDays(60), today)) {
            if (stat.scheduled() == 0) {
                continue;
            }
            String key = stat.date().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,
                    java.util.Locale.ENGLISH);
            int[] acc = byWeekday.computeIfAbsent(key, k -> new int[2]);
            acc[0] += stat.completed();
            acc[1] += stat.scheduled();
        }
        byWeekday.entrySet().stream()
                .filter(e -> e.getValue()[1] >= 4)
                .max((a, b) -> Double.compare(Numbers.percent(a.getValue()[0], a.getValue()[1]),
                        Numbers.percent(b.getValue()[0], b.getValue()[1])))
                .ifPresent(e -> insights.add(new InsightResponse("BEST_DAY", "Your strongest day",
                        e.getKey() + " is when you complete the most: "
                                + Numbers.percent(e.getValue()[0], e.getValue()[1])
                                + "% over the last 60 days. Schedule demanding habits there.",
                        "bi-calendar-check", "info")));

        List<LocalDateTime> stamps = completionRepository.createdTimestamps(userId, today.minusDays(60), today);
        if (stamps.size() >= 5) {
            int[] buckets = new int[4];
            for (LocalDateTime ts : stamps) {
                int hour = ts.getHour();
                buckets[hour < 9 ? 0 : hour < 13 ? 1 : hour < 18 ? 2 : 3]++;
            }
            String[] labels = {"early morning (before 09:00)", "late morning (09:00-13:00)",
                    "afternoon (13:00-18:00)", "evening (after 18:00)"};
            int best = 0;
            for (int i = 1; i < buckets.length; i++) {
                if (buckets[i] > buckets[best]) {
                    best = i;
                }
            }
            insights.add(new InsightResponse("BEST_TIME", "When you actually tick habits off",
                    "Most of your completions (" + buckets[best] + " of " + stamps.size() + ") happen in the "
                            + labels[best] + ". Put your reminder there and the habit will feel effortless.",
                    "bi-clock-history", "info"));
        }

        // 4. weekly summary
        List<MetricsService.DayStat> week = metrics.range(active, index, today.minusDays(6), today);
        int weekScheduled = metrics.totalScheduled(week);
        int weekDone = metrics.totalCompleted(week);
        if (weekScheduled > 0) {
            insights.add(new InsightResponse("WEEK_SUMMARY", "Last 7 days",
                    "You completed " + weekDone + " of " + weekScheduled + " scheduled sessions ("
                            + Numbers.percent(weekDone, weekScheduled) + "%). "
                            + (weekDone >= weekScheduled * 0.8
                            ? "This is a strong week - protect the routine."
                            : "Aim for one extra completion tomorrow; small wins compound."),
                    "bi-graph-up-arrow", "primary"));
        }

        // 5. streak encouragement
        int streak = metrics.currentPerfectStreak(active, index, today);
        if (streak >= 3) {
            insights.add(new InsightResponse("STREAK", streak + "-day perfect streak",
                    "Every habit scheduled on each of the last " + streak + " days was completed. "
                            + "Do not break the chain today.", "bi-fire", "success"));
        }

        // 6. pending today
        int pending = 0;
        for (Habit h : active) {
            if (streaks.isScheduled(h, today) && !metrics.datesFor(index, h.id()).contains(today)) {
                pending++;
            }
        }
        if (pending > 0) {
            insights.add(new InsightResponse("PENDING", pending + " habit" + (pending == 1 ? "" : "s") + " left today",
                    "Finish the smallest one first - momentum matters more than intensity.",
                    "bi-hourglass-split", "warning"));
        } else {
            insights.add(new InsightResponse("DONE_TODAY", "Today is complete",
                    "Nothing left on today's schedule. That is exactly how consistency is built.",
                    "bi-check2-circle", "success"));
        }

        return insights;
    }
}
