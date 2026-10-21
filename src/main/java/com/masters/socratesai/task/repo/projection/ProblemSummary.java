package com.masters.socratesai.task.repo.projection;

import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.task.model.TaskDifficulty;
import java.time.OffsetDateTime;

/** Closed projection: catalog queries do not select statements, starters or tests. */
public interface ProblemSummary {
    Long getId();
    String getTitle();
    String getTopic();
    TaskDifficulty getDifficulty();
    ContentStatus getStatus();
    ContentVisibility getVisibility();
    Long getPublishedVersionId();
    OffsetDateTime getUpdatedAt();
    Long getLockVersion();
}
