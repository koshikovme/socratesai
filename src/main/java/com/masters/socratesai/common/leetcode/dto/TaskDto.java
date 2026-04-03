package com.masters.socratesai.common.leetcode.dto;

import lombok.Data;

@Data
public class TaskDto {
    private String questionFrontendId;
    private String title;
    private String difficulty;
    private Boolean isPaidOnly;
    private String content;
}