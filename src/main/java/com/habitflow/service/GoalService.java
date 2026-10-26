package com.habitflow.service;

import com.habitflow.dto.GoalRequest;
import com.habitflow.dto.GoalResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.NotFoundException;
import com.habitflow.model.Goal;
import com.habitflow.model.Habit;
import com.habitflow.repository.GoalRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.util.Numbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Goals are either manual (a target number you increment yourself) or habit-driven
 * (progress = completions of the linked habits since the goal was created, measured
 * against the target value). Milestones at 25/50/75/100% are persisted on the row so
 * each one is announced exactly once.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;
    private final NotificationService notificationService;

    public GoalService(GoalRepository goalRepository, HabitRepository habitRepository,
                       NotificationService notificationService) {
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
        this.notificationService = notificationService;
    }

    public List<GoalResponse> list(Long userId) {
        List<Habit> habits = habitRepository.findByUser(userId, "ALL");
        Map<Long, List<Long>> links = goalRepository.linksFor(userId);
        List<GoalResponse> out = new ArrayList<>();
        for (Goal goal : goalRepository.findByUser(userId)) {
            out.add(toResponse(userId, goal, links.getOrDefault(goal.id(), List.of()), habits));
        }
        return out;
    }

    public GoalResponse get(Long userId, Long goalId) {
        Goal goal = goalRepository.findByIdAndUser(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found."));
        List<Habit> habits = habitRepository.findByUser(userId, "ALL");
        return toResponse(userId, goal, goalRepository.linksFor(userId).getOrDefault(goalId, List.of()), habits);
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        validateHabits(userId, request.habitIds());
        String status = request.status() == null ? "ACTIVE" : request.status().toUpperCase();
        Long id = goalRepository.insert(userId, request.title().trim(), trimToNull(request.description()),
                request.targetValue(), request.manualProgress() == null ? 0 : request.manualProgress(),
                request.targetDate(), status);
        goalRepository.linkHabits(id, request.habitIds());
        return get(userId, id);
    }

    @Transactional
    public GoalResponse update(Long userId, Long goalId, GoalRequest request) {
        Goal existing = goalRepository.findByIdAndUser(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found."));
        validateHabits(userId, request.habitIds());
        String status = request.status() == null ? existing.status() : request.status().toUpperCase();
        goalRepository.update(goalId, userId, request.title().trim(), trimToNull(request.description()),
                request.targetValue(),
                request.manualProgress() == null ? existing.manualProgress() : request.manualProgress(),
                request.targetDate(), status);
        goalRepository.linkHabits(goalId, request.habitIds());
        return get(userId, goalId);
    }

    @Transactional
    public GoalResponse setStatus(Long userId, Long goalId, String status) {
        if (!List.of("ACTIVE", "COMPLETED", "ARCHIVED").contains(status.toUpperCase())) {
            throw new BadRequestException("Status must be ACTIVE, COMPLETED or ARCHIVED.");
        }
        goalRepository.findByIdAndUser(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found."));
        goalRepository.updateStatus(goalId, userId, status.toUpperCase());
        return get(userId, goalId);
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        goalRepository.findByIdAndUser(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found."));
        goalRepository.delete(goalId, userId);
    }

    /** Re-evaluates every active goal and pushes a notification the first time each milestone is crossed. */
    @Transactional
    public void refreshMilestones(Long userId) {
        List<Habit> habits = habitRepository.findByUser(userId, "ALL");
        Map<Long, List<Long>> links = goalRepository.linksFor(userId);
        for (Goal goal : goalRepository.findByUser(userId)) {
            if (!"ACTIVE".equals(goal.status())) {
                continue;
            }
            GoalResponse response = toResponse(userId, goal,
                    links.getOrDefault(goal.id(), List.of()), habits);
            int milestone = response.milestoneReached();
            int stored = goalRepository.getMilestone(goal.id());
            if (milestone > stored) {
                goalRepository.updateMilestone(goal.id(), userId, milestone);
                notificationService.onMilestone(userId, goal.title(), milestone);
            }
        }
    }

    private GoalResponse toResponse(Long userId, Goal goal, List<Long> habitIds, List<Habit> habits) {
        int progress;
        String source;
        Integer target = goal.targetValue();
        if (!habitIds.isEmpty() && target != null && target > 0) {
            LocalDate since = goal.createdAt() == null ? null : goal.createdAt().toLocalDate();
            progress = goalRepository.countLinkedCompletions(goal.id(), userId, since);
            source = "HABITS";
        } else {
            progress = goal.manualProgress();
            source = "MANUAL";
        }
        int effectiveTarget = target == null || target <= 0 ? 100 : target;
        double percent = Numbers.percent(Math.min(progress, effectiveTarget), effectiveTarget);
        if ("COMPLETED".equals(goal.status())) {
            percent = 100.0;
            progress = Math.max(progress, effectiveTarget);
        }
        int milestone = percent >= 100 ? 100 : percent >= 75 ? 75 : percent >= 50 ? 50 : percent >= 25 ? 25 : 0;

        List<String> names = new ArrayList<>();
        for (Habit h : habits) {
            if (habitIds.contains(h.id())) {
                names.add(h.name());
            }
        }
        long daysRemaining = goal.targetDate() == null ? -1
                : ChronoUnit.DAYS.between(LocalDate.now(), goal.targetDate());

        return new GoalResponse(goal.id(), goal.title(), goal.description(), target, progress, percent,
                goal.targetDate(), daysRemaining, goal.status(), habitIds, names,
                milestoneMessage(goal.title(), milestone, percent), milestone, source);
    }

    private String milestoneMessage(String title, int milestone, double percent) {
        return switch (milestone) {
            case 100 -> "Goal complete - " + title + " reached 100%. Time to celebrate.";
            case 75 -> "Three quarters of the way there: " + title + " is at " + Math.round(percent) + "%.";
            case 50 -> "Halfway point reached on " + title + ". Keep going.";
            case 25 -> "Great start - " + title + " is already a quarter done.";
            default -> "Progress update: " + title + " is at " + Math.round(percent) + "%.";
        };
    }

    private void validateHabits(Long userId, List<Long> habitIds) {
        if (habitIds == null || habitIds.isEmpty()) {
            return;
        }
        for (Long habitId : habitIds) {
            if (!habitRepository.existsForUser(habitId, userId)) {
                throw new BadRequestException("One of the selected habits does not belong to you.");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
