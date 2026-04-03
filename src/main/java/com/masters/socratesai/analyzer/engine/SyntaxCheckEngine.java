package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.SyntaxCheckResult;
import org.springframework.stereotype.Component;

@Component
public class SyntaxCheckEngine {

    public SyntaxCheckResult check(String code, String language) {
        SyntaxCheckResult result = new SyntaxCheckResult();

        if (code == null || code.isBlank()) {
            result.setCompileSuccess(false);
            result.setSuspiciousRegion("empty editor");
            return result;
        }

        if ("java".equalsIgnoreCase(language) && !code.contains(";")) {
            result.setCompileSuccess(false);
            result.setSuspiciousRegion("statement ending");
            return result;
        }

        result.setCompileSuccess(true);
        return result;
    }
}