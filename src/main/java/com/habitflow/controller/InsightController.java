package com.habitflow.controller;

import com.habitflow.dto.InsightResponse;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;
    private final CurrentUser currentUser;

    public InsightController(InsightService insightService, CurrentUser currentUser) {
        this.insightService = insightService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<InsightResponse> insights() {
        return insightService.generate(currentUser.id());
    }
}
