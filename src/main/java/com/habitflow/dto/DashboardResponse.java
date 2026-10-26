package com.habitflow.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate today,
        String userName,
        int activeHabits,
        int completedToday,
        int scheduledToday,
        double todayPercent,
        int currentStreak,
        int bestStreak,
        double weeklyConsistency,
        double monthlyConsistency,
        int totalCompletions,
        int bestHabitStreak,
        String bestHabitName,
        List<TodayHabit> pendingToday,
        List<TodayHabit> completedTodayList,
        List<ChartPoint> weeklyChart,
        List<ChartPoint> monthlyChart,
        List<ChartPoint> categoryDistribution,
        List<TodayHabit> recentCompletions
) {
}
