package com.habitflow.controller;

import com.habitflow.dto.AnalyticsResponse;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService analyticsService, CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AnalyticsResponse analytics(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long habitId) {
        return analyticsService.build(currentUser.id(), from, to, habitId);
    }
}
