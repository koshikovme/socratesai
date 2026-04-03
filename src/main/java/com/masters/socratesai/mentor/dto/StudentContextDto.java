package com.masters.socratesai.mentor.dto;

import lombok.Data;

@Data
public class StudentContextDto {
    private int sameErrorCount;
    private int totalErrorsSeen;
    private String lastFeedbackAction;
    private Boolean lastFeedbackSuccess;
    private int totalFeedbackCountInSession;
}