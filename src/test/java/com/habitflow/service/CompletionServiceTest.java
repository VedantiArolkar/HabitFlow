package com.habitflow.service;

import com.habitflow.dto.CompletionRequest;
import com.habitflow.dto.HabitStatsResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.ConflictException;
import com.habitflow.exception.NotFoundException;
import com.habitflow.model.Habit;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Service-layer tests for the duplicate-completion and date guard rails. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompletionServiceTest {

    @Mock private HabitRepository habitRepository;
    @Mock private CompletionRepository completionRepository;
    @Mock private StreakCalculator streaks;
    @Mock private MetricsService metrics;
    @Mock private NotificationService notificationService;
    @Mock private GoalService goalService;

    @InjectMocks private CompletionService completionService;

    private Habit habit;
    private final Long userId = 7L;

    @BeforeEach
    void setUp() {
        habit = new Habit(11L, userId, 1L, "Health", "#14B8A6", "Drink 2L of water",
                "Hydration", new BigDecimal("2"), "litres", "DAILY", null,
                "#14B8A6", "bi-droplet", null, LocalDate.now().minusDays(10),
                "ACTIVE", new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5, 6, 7)), LocalDateTime.now());
        when(habitRepository.findByIdAndUser(11L, userId)).thenReturn(Optional.of(habit));
        when(streaks.isScheduled(any(Habit.class), any(LocalDate.class))).thenReturn(true);
    }

    @Test
    @DisplayName("completing a habit on a fresh date inserts one row")
    void completeInsertsOnce() {
        LocalDate today = LocalDate.now();
        when(completionRepository.exists(11L, today)).thenReturn(false);
        when(completionRepository.insert(eq(11L), eq(userId), eq(today), any())).thenReturn(1L);

        var result = completionService.complete(userId, 11L, new CompletionRequest(today, "Felt good", false));

        assertEquals(true, result.completed());
        assertEquals("Drink 2L of water", result.name());
        verify(completionRepository).insert(11L, userId, today, "Felt good");
        verify(notificationService).onHabitCompleted(userId, habit, today);
        verify(goalService).refreshMilestones(userId);
    }

    @Test
    @DisplayName("a second completion for the same habit and date is rejected with 409")
    void duplicateCompletionIsRejected() {
        LocalDate today = LocalDate.now();
        when(completionRepository.exists(11L, today)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> completionService.complete(userId, 11L, new CompletionRequest(today, null, false)));

        assertEquals(true, error.getMessage().contains("already marked complete"));
        verify(completionRepository, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("a future date cannot be completed unless allowFuture is explicitly true")
    void futureDatesAreBlocked() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThrows(BadRequestException.class,
                () -> completionService.complete(userId, 11L, new CompletionRequest(tomorrow, null, false)));
        verify(completionRepository, never()).exists(anyLong(), any());
    }

    @Test
    @DisplayName("a date before the habit start date is rejected")
    void beforeStartDateIsBlocked() {
        LocalDate tooEarly = habit.startDate().minusDays(1);

        assertThrows(BadRequestException.class,
                () -> completionService.complete(userId, 11L, new CompletionRequest(tooEarly, null, false)));
    }

    @Test
    @DisplayName("an archived habit cannot be tracked")
    void archivedHabitIsBlocked() {
        Habit archived = new Habit(12L, userId, 1L, "Health", "#14B8A6", "Old habit", null,
                null, null, "DAILY", null, "#14B8A6", "bi-droplet", null,
                LocalDate.now().minusDays(5), "ARCHIVED",
                new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5, 6, 7)), LocalDateTime.now());
        when(habitRepository.findByIdAndUser(12L, userId)).thenReturn(Optional.of(archived));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> completionService.complete(userId, 12L,
                        new CompletionRequest(LocalDate.now(), null, false)));
        assertEquals(true, error.getMessage().toLowerCase().contains("archived"));
    }

    @Test
    @DisplayName("another user's habit id is reported as not found, never accepted")
    void foreignHabitIsNotFound() {
        when(habitRepository.findByIdAndUser(99L, userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> completionService.complete(userId, 99L,
                        new CompletionRequest(LocalDate.now(), null, false)));
    }

    @Test
    @DisplayName("undoing a completion that does not exist returns 404")
    void undoMissingCompletionIsNotFound() {
        when(completionRepository.delete(11L, userId, LocalDate.now())).thenReturn(0);

        assertThrows(NotFoundException.class,
                () -> completionService.undo(userId, 11L, LocalDate.now()));
    }

    @Test
    @DisplayName("undo deletes exactly the requested habit and date")
    void undoDeletesOneRow() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(completionRepository.delete(11L, userId, yesterday)).thenReturn(1);

        completionService.undo(userId, 11L, yesterday);

        verify(completionRepository).delete(11L, userId, yesterday);
    }
}
