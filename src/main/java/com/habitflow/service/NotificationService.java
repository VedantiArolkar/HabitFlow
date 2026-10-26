package com.habitflow.service;

import com.habitflow.dto.NotificationResponse;
import com.habitflow.model.Habit;
import com.habitflow.model.Notification;
import com.habitflow.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * In-app notifications only. Everything here is persisted in MySQL and shown in the
 * bell menu / notifications page. There is NO email, SMS or web-push delivery - the
 * browser cannot be reached once the app tab is closed, so "reminders" are generated
 * the next time the user opens HabitFlow and are clearly labelled as in-app only.
 */
@Service
public class NotificationService {

    public static final String TYPE_COMPLETION = "COMPLETION";
    public static final String TYPE_REMINDER = "REMINDER";
    public static final String TYPE_MILESTONE = "MILESTONE";
    public static final String TYPE_WEEKLY = "WEEKLY_SUMMARY";
    public static final String TYPE_ACHIEVEMENT = "ACHIEVEMENT";

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void onHabitCompleted(Long userId, Habit habit, LocalDate date) {
        notificationRepository.insert(userId, TYPE_COMPLETION,
                "Habit completed: " + habit.name(),
                "Marked done for " + date + ". Keep the momentum going!",
                "/tracking?date=" + date);
    }

    @Transactional
    public void push(Long userId, String type, String title, String message, String link) {
        notificationRepository.insert(userId, type, title, message, link);
    }

    @Transactional
    public void onMilestone(Long userId, String goalTitle, int milestone) {
        notificationRepository.insert(userId, TYPE_MILESTONE,
                "Goal milestone reached: " + milestone + "%",
                "\"" + goalTitle + "\" is now " + milestone + "% complete. Excellent progress!",
                "/goals");
    }

    public List<NotificationResponse> list(Long userId, boolean unreadOnly, int limit) {
        List<Notification> rows = notificationRepository.findByUser(userId, unreadOnly, Math.min(limit, 100));
        return rows.stream().map(NotificationResponse::from).toList();
    }

    public int unreadCount(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        notificationRepository.markRead(id, userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        notificationRepository.delete(id, userId);
    }

    /**
     * Called when the user opens the app. Creates at most one reminder per day and one
     * weekly summary per calendar week, so the bell never fills with duplicates.
     */
    @Transactional
    public void refreshScheduledMessages(Long userId, List<Habit> habits) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalTime now = LocalTime.now();

        boolean reminderSent = notificationRepository.existsSince(userId, TYPE_REMINDER, startOfDay);
        if (!reminderSent) {
            int due = 0;
            String first = null;
            for (Habit h : habits) {
                if (!h.isActive() || h.reminderTime() == null) {
                    continue;
                }
                if (h.reminderTime().isAfter(now) || h.reminderTime().plusHours(1).isBefore(now)) {
                    continue;
                }
                due++;
                if (first == null) {
                    first = h.name();
                }
            }
            if (due > 0) {
                notificationRepository.insert(userId, TYPE_REMINDER,
                        "Habit reminder",
                        due == 1 ? "It is time for: " + first + "." : due + " habits are due around now, starting with " + first + ".",
                        "/tracking");
            }
        }

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean weeklySent = notificationRepository.existsSince(userId, TYPE_WEEKLY, weekStart.atStartOfDay());
        if (!weeklySent && today.getDayOfWeek() == DayOfWeek.MONDAY) {
            notificationRepository.insert(userId, TYPE_WEEKLY,
                    "A new week begins",
                    "Last week is closed. Set a small, achievable target for this week.",
                    "/analytics");
        }
    }
}
