package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service @RequiredArgsConstructor @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentProblemService {
    private final TaskRepository problems;
    private final ProblemAccess access;
    private final ProblemDtoMapper mapper;

    public PageResponse<StudentProblemSummaryResponse> list(int page, int size) {
        return PageResponse.from(problems.studentCatalog(access.readableVisibilities(), PageResponse.request(page, size))
                .map(item -> new StudentProblemSummaryResponse(item.getId(), item.getPublishedVersionId(), item.getTitle(), item.getTopic(), item.getDifficulty())));
    }
    public ProblemResponse get(Long id, Long versionId) { return mapper.student(access.published(id, versionId)); }
}
