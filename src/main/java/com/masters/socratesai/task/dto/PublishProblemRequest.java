package com.masters.socratesai.task.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PublishProblemRequest(@NotNull @PositiveOrZero Long lockVersion, @NotNull UUID verificationId) {}
