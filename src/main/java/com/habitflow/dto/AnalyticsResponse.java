package com.habitflow.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        int scheduledSessions,
        int completedSessions,
        double overallRate,
        double dailyRate,
        double weeklyRate,
        double monthlyRate,
        List<ChartPoint> trend,
        List<ChartPoint> habitPerformance,
        List<ChartPoint> categoryPerformance,
        List<HeatmapCell> heatmap,
        List<HabitStatsResponse> habitStats,
        List<String> bestHabits,
        List<String> lowHabits
) {
}
