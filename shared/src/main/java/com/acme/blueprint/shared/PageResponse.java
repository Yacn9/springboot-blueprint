package com.acme.blueprint.shared;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of a list endpoint. {@code page} is zero-based, matching Spring
 * Data's {@code ?page=} request parameter.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
