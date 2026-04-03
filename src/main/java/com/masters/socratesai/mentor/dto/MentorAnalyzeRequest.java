package com.masters.socratesai.mentor.dto;

import lombok.Data;

@Data
public class MentorAnalyzeRequest {
    private Long studentId;
    private Long taskId;
    private String language;
    private String code;
    private Integer attemptNo;
}
