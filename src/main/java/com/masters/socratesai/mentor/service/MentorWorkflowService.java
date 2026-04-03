package com.masters.socratesai.mentor.service;

import com.masters.socratesai.analyzer.dto.AnalyzerRequest;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.analyzer.service.AnalyzerService;
import com.masters.socratesai.mentor.dto.MentorAnalyzeRequest;
import com.masters.socratesai.mentor.dto.MentorRequest;
import com.masters.socratesai.mentor.dto.MentorResponse;
import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.service.StudentTaskSessionService;
import org.springframework.stereotype.Service;

@Service
public class MentorWorkflowService {

    private final AnalyzerService analyzerService;
    private final MentorService mentorService;
    private final StudentTaskSessionService sessionService;

    public MentorWorkflowService(
            AnalyzerService analyzerService,
            MentorService mentorService,
            StudentTaskSessionService sessionService
    ) {
        this.analyzerService = analyzerService;
        this.mentorService = mentorService;
        this.sessionService = sessionService;
    }

    public MentorResponse analyzeAndMentor(MentorAnalyzeRequest request) {
        return analyzeAndMentor(request, true);
    }

    public MentorResponse analyzeAndMentor(MentorAnalyzeRequest request, boolean countAsAttempt) {
        StudentTaskSession session = sessionService.getOrCreateActiveSession(
                request.getStudentId(),
                request.getTaskId()
        );

        if (countAsAttempt) {
            sessionService.incrementAttempts(session.getSessionId());
        }

        AnalyzerRequest analyzerRequest = new AnalyzerRequest();
        analyzerRequest.setStudentId(request.getStudentId());
        analyzerRequest.setTaskId(request.getTaskId());
        analyzerRequest.setLanguage(request.getLanguage());
        analyzerRequest.setCode(request.getCode());
        analyzerRequest.setAttemptNo(request.getAttemptNo());

        AnalyzerResult analyzerResult = analyzerService.analyze(analyzerRequest);

        MentorRequest mentorRequest = new MentorRequest();
        mentorRequest.setStudentId(request.getStudentId());
        mentorRequest.setSessionId(session.getSessionId());
        mentorRequest.setTaskId(request.getTaskId());
        mentorRequest.setAttemptNo(request.getAttemptNo());
        mentorRequest.setCode(request.getCode());
        mentorRequest.setAnalyzerResult(analyzerResult);

        return mentorService.mentor(mentorRequest);
    }
}
