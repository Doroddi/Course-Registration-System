package com.doroddi.courseregistration.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PageResponse {
        // 원본 목록의 변경으로 만들어진 응답 내용이 바뀌지 않도록 복사한다.
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
