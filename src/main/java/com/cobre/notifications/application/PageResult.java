package com.cobre.notifications.application;

import java.util.List;

public record PageResult<T>(
        List<T> data,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        if (totalElements == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }
}
