package com.masters.socratesai.common.leetcode.dto.graphql;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphQLRequest<T> {
    private String query;
    private T variables;
}