package com.masters.socratesai.mentor.controller;

import com.masters.socratesai.mentor.dto.MentorAnalyzeRequest;
import com.masters.socratesai.mentor.dto.MentorRequest;
import com.masters.socratesai.mentor.dto.MentorResponse;
import com.masters.socratesai.mentor.service.MentorService;
import com.masters.socratesai.mentor.service.MentorWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor")
public class MentorController {

    private final MentorService mentorService;
    private final MentorWorkflowService mentorWorkflowService;

    public MentorController(MentorService mentorService, MentorWorkflowService mentorWorkflowService) {
        this.mentorService = mentorService;
        this.mentorWorkflowService = mentorWorkflowService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<MentorResponse> feedback(@RequestBody MentorRequest request) {
        return ResponseEntity.ok(mentorService.mentor(request));
    }

    @PostMapping("/analyze-feedback")
    public ResponseEntity<MentorResponse> analyzeFeedback(@RequestBody MentorAnalyzeRequest request) {
        return ResponseEntity.ok(mentorWorkflowService.analyzeAndMentor(request));
    }
}
