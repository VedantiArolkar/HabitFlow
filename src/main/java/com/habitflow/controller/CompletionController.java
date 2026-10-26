package com.habitflow.controller;

import com.habitflow.dto.ApiMessage;
import com.habitflow.dto.CalendarDay;
import com.habitflow.dto.CompletionRequest;
import com.habitflow.dto.TodayHabit;
import com.habitflow.dto.TodayView;
import com.habitflow.model.HabitCompletion;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.CompletionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CompletionController {

    private final CompletionService completionService;
    private final CurrentUser currentUser;

    public CompletionController(CompletionService completionService, CurrentUser currentUser) {
        this.completionService = completionService;
        this.currentUser = currentUser;
    }

    /** Tracking screen for any date; defaults to today. */
    @GetMapping("/tracking")
    public TodayView tracking(@RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return completionService.viewForDate(currentUser.id(), date);
    }

    @PostMapping("/habits/{habitId}/complete")
    public ResponseEntity<TodayHabit> complete(@PathVariable Long habitId,
                                               @Valid @RequestBody CompletionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(completionService.complete(currentUser.id(), habitId, request));
    }

    @DeleteMapping("/habits/{habitId}/complete")
    public ApiMessage undo(@PathVariable Long habitId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        completionService.undo(currentUser.id(), habitId, date);
        return ApiMessage.ok("Completion removed.");
    }

    @GetMapping("/completions")
    public List<HabitCompletion> history(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return completionService.history(currentUser.id(), from, to);
    }

    @GetMapping("/calendar")
    public List<CalendarDay> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) Long habitId) {
        return completionService.calendar(currentUser.id(), from, to, habitId);
    }
}
