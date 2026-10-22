package com.masters.socratesai.common.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RevisionRequest(@NotNull @PositiveOrZero Long lockVersion) {}
