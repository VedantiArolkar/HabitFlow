package com.habitflow.controller;

import com.habitflow.dto.ApiMessage;
import com.habitflow.dto.GoalRequest;
import com.habitflow.dto.GoalResponse;
import com.habitflow.dto.StatusRequest;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;
    private final CurrentUser currentUser;

    public GoalController(GoalService goalService, CurrentUser currentUser) {
        this.goalService = goalService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GoalResponse> list() {
        return goalService.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable Long id) {
        return goalService.get(currentUser.id(), id);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goalService.create(currentUser.id(), request));
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return goalService.update(currentUser.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public GoalResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return goalService.setStatus(currentUser.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    public ApiMessage delete(@PathVariable Long id) {
        goalService.delete(currentUser.id(), id);
        return ApiMessage.ok("Goal deleted.");
    }
}
