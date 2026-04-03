package com.masters.socratesai.mentor.service;

import com.masters.socratesai.interaction.service.InteractionLogService;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StudentContextService {

    private final InteractionLogService interactionLogService;

    public StudentContextService(InteractionLogService interactionLogService) {
        this.interactionLogService = interactionLogService;
    }

    public StudentContextDto buildContext(Long studentId, UUID sessionId, Long taskId, String currentErrorType) {
        return interactionLogService.buildStudentContext(sessionId, studentId, taskId, currentErrorType);
    }
}