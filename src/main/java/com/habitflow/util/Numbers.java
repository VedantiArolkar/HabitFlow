package com.habitflow.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small numeric/formatting helpers shared by services and DTO builders. */
public final class Numbers {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Numbers() {
    }

    /** Percentage rounded to one decimal, safe for a zero or negative denominator. */
    public static double percent(int part, int total) {
        if (total <= 0) {
            return 0.0;
        }
        double raw = part * 100.0 / total;
        return Math.round(raw * 10.0) / 10.0;
    }

    public static double percent(double part, double total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((part * 100.0 / total) * 10.0) / 10.0;
    }

    public static int clampPercent(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    public static String shortDate(LocalDate date) {
        return date.format(SHORT_DATE);
    }

    public static String weekdayShort(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
    }
}
