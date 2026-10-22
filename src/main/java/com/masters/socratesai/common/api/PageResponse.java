package com.masters.socratesai.common.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {
    public static PageRequest request(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        return PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
