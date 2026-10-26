package com.habitflow.dto;

import java.time.LocalDate;

public record HeatmapCell(LocalDate date, int completed, int scheduled, int level) {
}
