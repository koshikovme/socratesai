package com.masters.socratesai.mentor.controller;

import com.masters.socratesai.mentor.nativecontext.*;
import com.masters.socratesai.websocket.config.*;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;

@RestController @RequiredArgsConstructor
public class NativeMentorController {
    private final MentorJwtAuthentication authentication;
    private final MentorSocketSessions admission;
    private final NativeMentorCoordinator coordinator;
    @PostMapping("/api/mentor/native-feedback")
    public CompletableFuture<NativeMentorFeedback> feedback(@RequestHeader("Authorization") String header, @RequestBody @Valid CodeUpdateMessage request) {
        var actor = authentication.authenticate(header).getIdentity(); admission.rate(actor.getStudentId());
        return coordinator.submit(actor, null, request);
    }
}
