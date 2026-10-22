package com.masters.socratesai.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties({"studentId", "taskId", "attemptNo", "analyzerResult", "studentContext"})
@ToString(onlyExplicitlyIncluded = true)
public class CodeUpdateMessage {
    @NotNull @Positive private Long problemId;
    @NotNull @Positive private Long problemVersionId;
    @NotBlank @Pattern(regexp = LANGUAGE_KEY) private String languageKey;
    @NotNull private UUID editorSessionId;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @ToString.Include private Long codeRevision;
    @NotNull @Size(max = SOURCE_CHARACTERS) private String code;
}
