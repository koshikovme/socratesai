package com.masters.socratesai.websocket.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CodeUpdateMessage {
    private Long studentId;
    private Long taskId;
    private String language;
    private String code;
    private Integer attemptNo;
}