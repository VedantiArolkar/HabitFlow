package com.habitflow.dto;

/** Generic label/value point used by Chart.js payloads. */
public record ChartPoint(String label, double value, String extra) {
    public static ChartPoint of(String label, double value) {
        return new ChartPoint(label, value, null);
    }
}
