package com.habitflow.service;

import com.habitflow.dto.AnalyticsResponse;
import com.habitflow.dto.ChartPoint;
import com.habitflow.dto.HeatmapCell;
import com.habitflow.dto.HabitStatsResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.model.Habit;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyticsService {

    private final HabitRepository habitRepository;
    private final HabitService habitService;
    private final MetricsService metrics;
    private final StreakCalculator streaks;

    public AnalyticsService(HabitRepository habitRepository, HabitService habitService,
                            MetricsService metrics, StreakCalculator streaks) {
        this.habitRepository = habitRepository;
        this.habitService = habitService;
        this.metrics = metrics;
        this.streaks = streaks;
    }

    public AnalyticsResponse build(Long userId, LocalDate from, LocalDate to, Long habitId) {
        LocalDate today = LocalDate.now();
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must be before the end date.");
        }
        if (start.plusDays(400).isBefore(end)) {
            throw new BadRequestException("Please choose a range of at most 400 days.");
        }

        List<Habit> all = habitRepository.findByUser(userId, "ALL");
        List<Habit> habits = habitId == null ? all
                : all.stream().filter(h -> h.id().equals(habitId)).toList();
        if (habitId != null && habits.isEmpty()) {
            throw new BadRequestException("Habit not found.");
        }
        Map<Long, Set<LocalDate>> index = metrics.completionIndex(userId, all);
        List<MetricsService.DayStat> days = metrics.range(habits, index, start, end);
        int scheduled = metrics.totalScheduled(days);
        int completed = metrics.totalCompleted(days);
        double overall = Numbers.percent(completed, scheduled);

        LocalDate weekFrom = today.minusDays(6);
        LocalDate monthFrom = today.minusDays(29);
        double dailyRate = Numbers.percent(metrics.totalCompleted(metrics.range(habits, index, today, today)),
                metrics.totalScheduled(metrics.range(habits, index, today, today)));
        double weeklyRate = Numbers.percent(metrics.totalCompleted(metrics.range(habits, index, weekFrom, today)),
                metrics.totalScheduled(metrics.range(habits, index, weekFrom, today)));
        double monthlyRate = Numbers.percent(metrics.totalCompleted(metrics.range(habits, index, monthFrom, today)),
                metrics.totalScheduled(metrics.range(habits, index, monthFrom, today)));

        List<ChartPoint> trend = new ArrayList<>();
        for (MetricsService.DayStat stat : days) {
            trend.add(new ChartPoint(stat.date().toString(), stat.percent(),
                    stat.completed() + "/" + stat.scheduled()));
        }

        List<HabitStatsResponse> stats = new ArrayList<>();
        List<ChartPoint> habitPerformance = new ArrayList<>();
        for (Habit h : habits) {
            HabitStatsResponse s = habitService.stats(userId, h.id(), start, end);
            stats.add(s);
            habitPerformance.add(ChartPoint.of(h.name(), s.overallRate()));
        }

        Map<String, int[]> byCategory = new LinkedHashMap<>();
        for (Habit h : habits) {
            String key = h.categoryName() == null ? "Other" : h.categoryName();
            int[] acc = byCategory.computeIfAbsent(key, k -> new int[2]);
            Set<LocalDate> dates = metrics.datesFor(index, h.id());
            acc[0] += streaks.completedScheduledCount(h, dates, start, end);
            acc[1] += streaks.scheduledCount(h, start, end);
        }
        List<ChartPoint> categoryPerformance = new ArrayList<>();
        byCategory.forEach((name, acc) -> categoryPerformance.add(ChartPoint.of(name, Numbers.percent(acc[0], acc[1]))));

        List<HeatmapCell> heatmap = new ArrayList<>();
        for (MetricsService.DayStat stat : days) {
            heatmap.add(new HeatmapCell(stat.date(), stat.completed(), stat.scheduled(), level(stat)));
        }

        List<String> sortedBest = stats.stream()
                .filter(s -> s.scheduledDaysInRange() > 0)
                .sorted((a, b) -> Double.compare(b.overallRate(), a.overallRate()))
                .map(HabitStatsResponse::habitName).toList();

        List<String> best = sortedBest.stream().limit(3).toList();
        List<String> low = stats.stream()
                .filter(s -> s.scheduledDaysInRange() > 2 && s.overallRate() < 60)
                .sorted((a, b) -> Double.compare(a.overallRate(), b.overallRate()))
                .map(HabitStatsResponse::habitName).toList();

        return new AnalyticsResponse(start, end, scheduled, completed, overall, dailyRate, weeklyRate,
                monthlyRate, trend, habitPerformance, categoryPerformance, heatmap, stats, best, low);
    }

    private int level(MetricsService.DayStat stat) {
        if (stat.scheduled() == 0) {
            return 0;
        }
        double p = stat.percent();
        if (p >= 100) {
            return 4;
        }
        if (p >= 75) {
            return 3;
        }
        if (p >= 50) {
            return 2;
        }
        return 1;
    }
}
