package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.SyntaxCheckResult;
import com.masters.socratesai.common.validation.ContesterLimits;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Non-executing legacy edit heuristic. compileSuccess is a compatibility estimate,
 * never a compiler result. All actual compilation belongs to CodeExecutionPort.
 */
@Component
public class SyntaxCheckEngine {
    private static final List<String> JAVA_DECLARATIONS = List.of("int ", "long ", "double ", "float ", "boolean ", "char ", "String ", "var ");

    public SyntaxCheckResult check(String code, String language) {
        SyntaxCheckResult result = new SyntaxCheckResult();
        if (code == null || code.isBlank()) {
            result.setSuspiciousRegion("empty editor");
            return result;
        }
        if (code.length() > ContesterLimits.SOURCE_CHARACTERS) {
            result.setSuspiciousRegion("source size limit");
            return result;
        }
        String lastLine = code.substring(code.lastIndexOf('\n') + 1).strip();
        boolean obviousMissingTerminator = "java".equalsIgnoreCase(language) && lastLine.contains("=")
                && JAVA_DECLARATIONS.stream().anyMatch(lastLine::startsWith)
                && !lastLine.endsWith(";") && !lastLine.endsWith("}") && !lastLine.endsWith("{");
        result.setCompileSuccess(!obviousMissingTerminator);
        if (obviousMissingTerminator) result.setSuspiciousRegion("statement ending");
        return result;
    }
}
