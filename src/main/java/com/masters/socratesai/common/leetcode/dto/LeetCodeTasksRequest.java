package com.masters.socratesai.common.leetcode.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LeetCodeTasksRequest {
    private String categorySlug;
    private Integer skip = 0;
    private Integer limit = 20;
    private Map<String, Object> filters = Map.of();
}