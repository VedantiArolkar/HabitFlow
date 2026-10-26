package com.habitflow.controller;

import com.habitflow.dto.ApiMessage;
import com.habitflow.dto.HabitRequest;
import com.habitflow.dto.HabitStatsResponse;
import com.habitflow.dto.StatusRequest;
import com.habitflow.model.Habit;
import com.habitflow.model.HabitCategory;
import com.habitflow.repository.CategoryRepository;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class HabitController {

    private final HabitService habitService;
    private final CategoryRepository categoryRepository;
    private final CurrentUser currentUser;

    public HabitController(HabitService habitService, CategoryRepository categoryRepository,
                           CurrentUser currentUser) {
        this.habitService = habitService;
        this.categoryRepository = categoryRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/habits")
    public List<Habit> list(@RequestParam(required = false, defaultValue = "ALL") String status) {
        return habitService.list(currentUser.id(), status);
    }

    @GetMapping("/habits/{id}")
    public Habit get(@PathVariable Long id) {
        return habitService.getOwned(id, currentUser.id());
    }

    @PostMapping("/habits")
    public ResponseEntity<Habit> create(@Valid @RequestBody HabitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(habitService.create(currentUser.id(), request));
    }

    @PutMapping("/habits/{id}")
    public Habit update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        return habitService.update(currentUser.id(), id, request);
    }

    @PatchMapping("/habits/{id}/status")
    public Habit changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return habitService.setStatus(currentUser.id(), id, request.status());
    }

    @DeleteMapping("/habits/{id}")
    public ApiMessage delete(@PathVariable Long id) {
        habitService.delete(currentUser.id(), id);
        return ApiMessage.ok("Habit deleted.");
    }

    @GetMapping("/habits/{id}/stats")
    public HabitStatsResponse stats(@PathVariable Long id,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return habitService.stats(currentUser.id(), id, from, to);
    }

    @GetMapping("/categories")
    public List<HabitCategory> categories() {
        return categoryRepository.findVisibleTo(currentUser.id());
    }

    @PostMapping("/categories")
    public ResponseEntity<HabitCategory> createCategory(@RequestBody HabitCategory request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new com.habitflow.exception.BadRequestException("Category name is required.");
        }
        Long id = categoryRepository.insert(name,
                request.color() == null || request.color().isBlank() ? "#7C6BFF" : request.color(),
                request.icon() == null || request.icon().isBlank() ? "bi-tag" : request.icon(),
                currentUser.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryRepository.findById(id).orElseThrow());
    }
}
