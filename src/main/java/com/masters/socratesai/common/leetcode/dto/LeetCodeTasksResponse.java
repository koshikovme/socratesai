package com.masters.socratesai.common.leetcode.dto;

import lombok.Data;

import lombok.AllArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
public class LeetCodeTasksResponse {
    private Integer totalNum;
    private List<TaskDto> tasks;
}