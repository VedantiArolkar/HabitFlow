package com.habitflow.service;

import com.habitflow.model.Habit;
import com.habitflow.model.HabitCompletion;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns raw completion rows into per-day scheduled/completed statistics.
 * Everything the dashboard, analytics and insights show is derived from here,
 * so there is exactly one definition of "scheduled" and "completed".
 */
@Service
public class MetricsService {

    private final CompletionRepository completionRepository;
    private final StreakCalculator streaks;

    public MetricsService(CompletionRepository completionRepository, StreakCalculator streaks) {
        this.completionRepository = completionRepository;
        this.streaks = streaks;
    }

    /** Scheduled vs completed on a single day. */
    public record DayStat(LocalDate date, int scheduled, int completed) {
        public double percent() {
            return Numbers.percent(completed, scheduled);
        }

        public boolean perfect() {
            return scheduled > 0 && completed >= scheduled;
        }
    }

    /** One query -> habitId => set of completed dates, reused by every calculation on a page load. */
    public Map<Long, Set<LocalDate>> completionIndex(Long userId, List<Habit> habits) {
        LocalDate to = LocalDate.now().plusDays(1);
        LocalDate from = to.minusDays(366);
        for (Habit h : habits) {
            if (h.startDate() != null && h.startDate().isBefore(from)) {
                from = h.startDate();
            }
        }
        Map<Long, Set<LocalDate>> index = new HashMap<>();
        for (HabitCompletion c : completionRepository.findByUserAndRange(userId, from, to)) {
            index.computeIfAbsent(c.habitId(), k -> new HashSet<>()).add(c.completionDate());
        }
        return index;
    }

    public Set<LocalDate> datesFor(Map<Long, Set<LocalDate>> index, Long habitId) {
        return index.getOrDefault(habitId, Set.of());
    }

    public DayStat dayStat(List<Habit> habits, Map<Long, Set<LocalDate>> index, LocalDate date) {
        int scheduled = 0;
        int completed = 0;
        for (Habit h : habits) {
            if (streaks.isScheduled(h, date)) {
                scheduled++;
                if (datesFor(index, h.id()).contains(date)) {
                    completed++;
                }
            }
        }
        return new DayStat(date, scheduled, completed);
    }

    public List<DayStat> range(List<Habit> habits, Map<Long, Set<LocalDate>> index,
                               LocalDate from, LocalDate to) {
        List<DayStat> stats = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            stats.add(dayStat(habits, index, cursor));
            cursor = cursor.plusDays(1);
        }
        return stats;
    }

    public int totalScheduled(List<DayStat> stats) {
        return stats.stream().mapToInt(DayStat::scheduled).sum();
    }

    public int totalCompleted(List<DayStat> stats) {
        return stats.stream().mapToInt(DayStat::completed).sum();
    }

    /**
     * Consecutive "perfect days": every habit scheduled that day was completed.
     * Today is only judged once it is over, so an unfinished today does not break the streak.
     */
    public int currentPerfectStreak(List<Habit> habits, Map<Long, Set<LocalDate>> index, LocalDate today) {
        if (habits.isEmpty()) {
            return 0;
        }
        LocalDate cursor = today;
        DayStat todayStat = dayStat(habits, index, today);
        if (todayStat.scheduled() > 0 && !todayStat.perfect()) {
            cursor = today.minusDays(1);
        }
        LocalDate earliest = earliestStart(habits);
        int streak = 0;
        while (!cursor.isBefore(earliest)) {
            DayStat stat = dayStat(habits, index, cursor);
            if (stat.scheduled() == 0) {
                cursor = cursor.minusDays(1);
                continue;
            }
            if (stat.perfect()) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    public int longestPerfectStreak(List<Habit> habits, Map<Long, Set<LocalDate>> index, LocalDate today) {
        if (habits.isEmpty()) {
            return 0;
        }
        LocalDate cursor = earliestStart(habits);
        int best = 0;
        int run = 0;
        while (!cursor.isAfter(today)) {
            DayStat stat = dayStat(habits, index, cursor);
            if (stat.scheduled() == 0) {
                cursor = cursor.plusDays(1);
                continue;
            }
            if (stat.perfect()) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
            cursor = cursor.plusDays(1);
        }
        return best;
    }

    public LocalDate earliestStart(List<Habit> habits) {
        LocalDate earliest = LocalDate.now();
        for (Habit h : habits) {
            if (h.startDate() != null && h.startDate().isBefore(earliest)) {
                earliest = h.startDate();
            }
        }
        return earliest;
    }
}
