package com.masters.socratesai.analyzer.engine;

import org.springframework.stereotype.Component;

@Component
public class ProgressEstimator {
    public Integer estimateTestsPassed(String code, Long taskId) {
        return 1;
    }

    public Integer estimateTestsFailed(String code, Long taskId) {
        return 2;
    }
}
