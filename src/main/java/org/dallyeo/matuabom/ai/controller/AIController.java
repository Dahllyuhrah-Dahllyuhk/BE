package org.dallyeo.matuabom.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.ai.dto.AIRequestDTO;
import org.dallyeo.matuabom.ai.service.AIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class AIController {

    private final AIService aiService;

    @PostMapping("/call")
    public ResponseEntity<?> call(
        @RequestBody AIRequestDTO request
    ) throws java.io.IOException, java.security.GeneralSecurityException {

        ResponseEntity<?> response = aiService.call(request);
        log.info("AI call response status: {}", response.getStatusCode());
        return response;
    }

}
