package com.masters.socratesai.analyzer.controller;

import com.masters.socratesai.analyzer.dto.AnalyzerRequest;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.analyzer.service.AnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyzer")
public class AnalyzerController {

    private final AnalyzerService analyzerService;

    public AnalyzerController(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzerResult> analyze(@RequestBody AnalyzerRequest request) {
        return ResponseEntity.ok(analyzerService.analyze(request));
    }
}