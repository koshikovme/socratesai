package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.domain.ExecutionLimits;
import com.masters.socratesai.execution.model.ExecutionSnapshot;
import com.masters.socratesai.task.model.ProblemLanguageConfig;
import lombok.experimental.UtilityClass;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@UtilityClass
public class ExecutionSnapshots {
    public String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Source digest unavailable"); }
    }

    public ExecutionSnapshot create(Long problemId, ProblemLanguageConfig language, String source) {
        return ExecutionSnapshot.builder().problemId(problemId).problemVersionId(language.getProblemVersion().getId())
                .languageKey(language.getLanguageKey()).runtimeKey(language.getRuntimeKey()).runtimeVersion(language.getRuntimeVersion())
                .sourceCode(source).sourceHash(hash(source)).timeLimitMs(language.getTimeLimitMs()).wallTimeLimitMs(language.getWallTimeLimitMs())
                .memoryLimitKb(language.getMemoryLimitKb()).outputLimitBytes(language.getOutputLimitBytes()).build();
    }

    public ExecutionLimits limits(ExecutionSnapshot snapshot) {
        return ExecutionLimits.builder().timeLimitMs(snapshot.getTimeLimitMs()).wallTimeLimitMs(snapshot.getWallTimeLimitMs())
                .memoryLimitKb(snapshot.getMemoryLimitKb()).outputLimitBytes(snapshot.getOutputLimitBytes()).build();
    }
}
