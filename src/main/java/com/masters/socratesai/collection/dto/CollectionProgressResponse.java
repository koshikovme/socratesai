package com.masters.socratesai.collection.dto;

import java.util.List;

public record CollectionProgressResponse(Long collectionId, int total, int solved, List<ProblemProgress> problems) {
    public record ProblemProgress(Long problemId, Long problemVersionId, boolean solved) {}
}
