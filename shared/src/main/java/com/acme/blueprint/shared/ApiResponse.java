package com.acme.blueprint.shared;

/**
 * Success envelope for every REST endpoint: {@code { "data": ... }}.
 *
 * <p>Errors are deliberately not wrapped — they are RFC 9457 problem details,
 * so a client tells success from failure by status code, not by inspecting
 * the body.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
