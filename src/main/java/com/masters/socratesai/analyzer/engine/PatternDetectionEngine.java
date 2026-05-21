package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.ErrorType;
import com.masters.socratesai.analyzer.model.PatternDetectionResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class PatternDetectionEngine {

    public PatternDetectionResult detect(String code, Long taskId) {
        PatternDetectionResult result = new PatternDetectionResult();
        Map<String, Object> signals = new HashMap<>();

        if (code == null || code.isBlank()) {
            result.setErrorType(ErrorType.valueOf("STUCK_NO_PROGRESS"));
            result.setSeverity("MEDIUM");
            result.setSuspiciousRegion("empty editor");
            result.setSignals(signals);
            return result;
        }

        String normalized = code.toLowerCase(Locale.ROOT);
        if (normalized.contains("todo") || normalized.contains("unsupportedoperationexception")) {
            result.setErrorType(ErrorType.valueOf("STUCK_NO_PROGRESS"));
            result.setSeverity("MEDIUM");
            result.setSuspiciousRegion("unfinished implementation");
            signals.put("unfinishedImplementation", true);
            result.setSignals(signals);
            return result;
        }

        if (code.contains("<=") && code.contains("for")) {
            result.setErrorType(ErrorType.valueOf("OFF_BY_ONE"));
            result.setSeverity("MEDIUM");
            result.setSuspiciousRegion("for loop condition");
            signals.put("loopBoundarySuspicious", true);
            result.setSignals(signals);
            return result;
        }

        if (normalized.contains("binary") || normalized.contains("mid")) {
            if (normalized.contains("left < right") || normalized.contains("low < high")) {
                result.setErrorType(ErrorType.valueOf("WRONG_LOOP_BOUNDARY"));
                result.setSeverity("MEDIUM");
                result.setSuspiciousRegion("binary search loop boundary");
                signals.put("binarySearchBoundarySuspicious", true);
                result.setSignals(signals);
                return result;
            }
        }

        if ((normalized.contains(".equals(") && !normalized.contains("null"))
                || (normalized.contains("stack.peek()") && !normalized.contains("isempty"))) {
            result.setErrorType(ErrorType.valueOf("POSSIBLE_NULL_ACCESS"));
            result.setSeverity("MEDIUM");
            result.setSuspiciousRegion("missing null or empty guard");
            signals.put("missingGuard", true);
            result.setSignals(signals);
            return result;
        }

        if (code.contains("while") && code.contains("true")) {
            result.setErrorType(ErrorType.valueOf("WRONG_CONDITION"));
            result.setSeverity("MEDIUM");
            result.setSuspiciousRegion("while condition");
            signals.put("possibleInfiniteLoop", true);
            result.setSignals(signals);
            return result;
        }

        result.setErrorType(ErrorType.valueOf("UNKNOWN"));
        result.setSeverity("LOW");
        result.setSuspiciousRegion("logic block");
        result.setSignals(signals);
        return result;
    }
}
