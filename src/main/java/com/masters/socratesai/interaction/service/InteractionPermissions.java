package com.masters.socratesai.interaction.service;

import com.masters.socratesai.interaction.dto.ExpertLabelRequest;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.task.repo.TaskRepository;
import com.masters.socratesai.user.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component("interactionPermissions") @RequiredArgsConstructor
public class InteractionPermissions {
    private final CurrentActor actors;
    private final InteractionLogRepository logs;
    private final TaskRepository problems;
    public boolean canReport(UUID id) {
        var actor = actors.require();
        return actor.admin() || logs.findById(id).filter(log -> log.getStudentId().equals(actor.id())).isPresent();
    }
    public boolean canLabel(UUID id, ExpertLabelRequest request) {
        var actor = actors.require();
        if (actor.admin()) return true;
        if (actor.role() != UserRole.TEACHER || !Objects.equals(actor.id(), request.getReviewerId())) return false;
        return logs.findById(id).filter(log -> log.getProblemId() != null).flatMap(log -> problems.findById(log.getProblemId()))
                .filter(problem -> Objects.equals(problem.getOwnerId(), actor.id())).isPresent();
    }
}
