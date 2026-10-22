package com.masters.socratesai.task.service;

import com.masters.socratesai.task.model.ProblemVersion;
import com.masters.socratesai.task.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/** Length-framed, ordered digest of the entire execution/publication snapshot. No raw content in errors. */
@Component @RequiredArgsConstructor
public class ProblemFingerprint {
    private final ProblemLanguageConfigRepository languages;
    private final ProblemTestCaseRepository tests;

    public String hash(ProblemVersion version) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Required content digest unavailable"); }
        Stream.of(version.getId(), version.getProblem().getId(), version.getVersionNumber(), version.getTitle(),
                version.getTopic(), version.getDifficulty(), version.getStatement(), version.getInputFormat(), version.getOutputFormat(),
                version.getConstraintsText(), version.getComparisonMode(), version.getVisibility()).forEach(value -> add(digest, value));
        var runtimeConfigs = languages.findAllByProblemVersion_IdOrderByLanguageKey(version.getId());
        add(digest, runtimeConfigs.size());
        runtimeConfigs.forEach(language -> Stream.of(language.getLanguageKey(), language.getRuntimeKey(), language.getRuntimeVersion(),
                language.getStarterCode(), language.getEntryPoint(), language.getTimeLimitMs(), language.getWallTimeLimitMs(),
                language.getMemoryLimitKb(), language.getOutputLimitBytes()).forEach(value -> add(digest, value)));
        var cases = tests.findAllByProblemVersion_IdOrderByPosition(version.getId());
        add(digest, cases.size());
        cases.forEach(test -> Stream.of(test.getId(), test.getPosition(), test.getInputData(), test.getExpectedOutput(), test.getHidden())
                .forEach(value -> add(digest, value)));
        return HexFormat.of().formatHex(digest.digest());
    }

    private void add(MessageDigest digest, Object value) {
        byte[] bytes = value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }
}
