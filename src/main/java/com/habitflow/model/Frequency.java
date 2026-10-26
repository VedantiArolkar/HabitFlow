package com.habitflow.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** Supported habit frequencies and the schedule days each one implies. */
public enum Frequency {
    DAILY,
    WEEKDAYS,
    WEEKLY,
    CUSTOM;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (Frequency f : values()) {
            if (f.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /** Default schedule used when the client does not send explicit days. */
    public static Set<Integer> defaultDays(String frequency) {
        Set<Integer> days = new LinkedHashSet<>();
        switch (frequency == null ? "" : frequency.toUpperCase()) {
            case "DAILY", "WEEKLY" -> {
                for (int d = 1; d <= 7; d++) {
                    days.add(d);
                }
            }
            case "WEEKDAYS" -> {
                days.add(1);
                days.add(2);
                days.add(3);
                days.add(4);
                days.add(5);
            }
            default -> {
            }
        }
        return days;
    }
}
