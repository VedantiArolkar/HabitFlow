package com.habitflow.controller;

import com.habitflow.dto.DashboardResponse;
import com.habitflow.model.Habit;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.DashboardService;
import com.habitflow.service.HabitService;
import com.habitflow.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final NotificationService notificationService;
    private final HabitService habitService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, NotificationService notificationService,
                               HabitService habitService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.notificationService = notificationService;
        this.habitService = habitService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        Long userId = currentUser.id();
        List<Habit> habits = habitService.list(userId, "ALL");
        // refresh in-app reminders / weekly summary once per day or week
        notificationService.refreshScheduledMessages(userId, habits);
        return dashboardService.build(userId, currentUser.principal().getFullName());
    }
}
