package com.masters.socratesai.collection.dto;

import java.util.List;

public record StudentCollectionResponse(Long id, String title, String description, List<CollectionProblemResponse> problems) {}
