package com.relationshiptemperature.api.common.api;

import java.util.List;

public record PagedResponse<T>(List<T> data, PageMeta meta) {

    public record PageMeta(String nextCursor, boolean hasNext) {}

    public static <T> PagedResponse<T> singlePage(List<T> data) {
        return new PagedResponse<>(data, new PageMeta(null, false));
    }
}
