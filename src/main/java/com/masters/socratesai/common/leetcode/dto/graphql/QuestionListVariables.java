package com.masters.socratesai.common.leetcode.dto.graphql;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class QuestionListVariables {
    private String categorySlug;
    private Integer limit;
    private Integer skip;
    private Map<String, Object> filters;
}