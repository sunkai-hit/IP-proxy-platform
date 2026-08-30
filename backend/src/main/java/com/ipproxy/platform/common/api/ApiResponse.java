package com.ipproxy.platform.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String requestId, long timestamp) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>("0", "success", data, currentRequestId(), System.currentTimeMillis()); }
    public static ApiResponse<Void> success() { return success(null); }
    public static <T> ApiResponse<T> failure(String code, String message) { return new ApiResponse<>(code, message, null, currentRequestId(), System.currentTimeMillis()); }
    private static String currentRequestId() { String value = MDC.get("requestId"); return value == null ? "" : value; }
}
