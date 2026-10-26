package com.habitflow.service;

import com.habitflow.dto.ChartPoint;
import com.habitflow.dto.DashboardResponse;
import com.habitflow.dto.TodayHabit;
import com.habitflow.dto.TodayView;
import com.habitflow.model.Habit;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardService {

    private final HabitRepository habitRepository;
    private final CompletionRepository completionRepository;
    private final CompletionService completionService;
    private final MetricsService metrics;
    private final StreakCalculator streaks;

    public DashboardService(HabitRepository habitRepository, CompletionRepository completionRepository,
                            CompletionService completionService, MetricsService metrics,
                            StreakCalculator streaks) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
        this.completionService = completionService;
        this.metrics = metrics;
        this.streaks = streaks;
    }

    public DashboardResponse build(Long userId, String userName) {
        LocalDate today = LocalDate.now();
        List<Habit> all = habitRepository.findByUser(userId, "ALL");
        List<Habit> active = all.stream().filter(Habit::isActive).toList();
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, all);

        TodayView todayView = completionService.viewForDate(userId, today);
        List<TodayHabit> pending = todayView.habits().stream().filter(h -> !h.completed()).toList();
        List<TodayHabit> done = todayView.habits().stream().filter(TodayHabit::completed).toList();

        // weekly consistency (last 7 days) and monthly (last 30 days), scheduled days only
        List<MetricsService.DayStat> week = metrics.range(active, index, today.minusDays(6), today);
        List<MetricsService.DayStat> month = metrics.range(active, index, today.minusDays(29), today);
        double weekly = Numbers.percent(metrics.totalCompleted(week), metrics.totalScheduled(week));
        double monthly = Numbers.percent(metrics.totalCompleted(month), metrics.totalScheduled(month));

        int currentStreak = metrics.currentPerfectStreak(active, index, today);
        int bestStreak = metrics.longestPerfectStreak(active, index, today);

        // best habit by streak
        int bestHabitStreak = 0;
        String bestHabitName = null;
        for (Habit h : active) {
            int s = streaks.currentStreak(h, metrics.datesFor(index, h.id()), today);
            if (s > bestHabitStreak) {
                bestHabitStreak = s;
                bestHabitName = h.name();
            }
        }

        List<ChartPoint> weeklyChart = new ArrayList<>();
        for (MetricsService.DayStat stat : week) {
            weeklyChart.add(new ChartPoint(Numbers.weekdayShort(stat.date()), stat.percent(),
                    stat.completed() + "/" + stat.scheduled()));
        }

        List<ChartPoint> monthlyChart = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        for (int i = 3; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
            List<MetricsService.DayStat> stats = metrics.range(active, index, from, to);
            monthlyChart.add(ChartPoint.of(ym.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                    Numbers.percent(metrics.totalCompleted(stats), metrics.totalScheduled(stats))));
        }

        List<ChartPoint> categories = new ArrayList<>();
        for (Map.Entry<String, Integer> e : categoryCounts(active).entrySet()) {
            categories.add(ChartPoint.of(e.getKey(), e.getValue()));
        }

        List<TodayHabit> recent = new ArrayList<>();
        for (Habit h : all) {
            if (completionRepository.exists(h.id(), today)) {
                recent.add(TodayHabit.of(h.id(), h.name(), h.icon(), h.color(), h.categoryName(),
                        h.frequency(), null, true, true, null,
                        streaks.currentStreak(h, metrics.datesFor(index, h.id()), today), h.description()));
            }
        }

        return new DashboardResponse(today, userName, active.size(), todayView.completedCount(),
                todayView.scheduledCount(), todayView.completionPercent(), currentStreak, bestStreak,
                weekly, monthly, completionRepository.countForUser(userId), bestHabitStreak, bestHabitName,
                pending, done, weeklyChart, monthlyChart, categories, recent);
    }

    private Map<String, Integer> categoryCounts(List<Habit> habits) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Habit h : habits) {
            counts.merge(h.categoryName() == null ? "Other" : h.categoryName(), 1, Integer::sum);
        }
        return counts;
    }
}
