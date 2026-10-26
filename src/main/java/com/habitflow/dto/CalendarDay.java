package com.habitflow.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarDay(LocalDate date, int scheduled, int completed, boolean complete,
                          List<TodayHabit> habits) {
}
