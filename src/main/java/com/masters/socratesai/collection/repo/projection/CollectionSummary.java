package com.masters.socratesai.collection.repo.projection;

import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import java.time.OffsetDateTime;

public interface CollectionSummary {
    Long getId();
    String getTitle();
    ContentStatus getStatus();
    ContentVisibility getVisibility();
    OffsetDateTime getUpdatedAt();
    Long getLockVersion();
}
