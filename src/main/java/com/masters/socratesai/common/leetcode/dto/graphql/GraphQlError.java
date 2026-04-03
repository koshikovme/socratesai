package com.masters.socratesai.common.leetcode.dto.graphql;

import java.util.List;
import java.util.Map;

public record GraphQlError(
        String message,
        List<Object> path,
        Map<String, Object> extensions
) {
}