package com.habitflow.dto;

public record ApiMessage(boolean success, String message) {
    public static ApiMessage ok(String message) {
        return new ApiMessage(true, message);
    }
}
