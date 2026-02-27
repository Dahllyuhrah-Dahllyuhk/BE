package org.dallyeo.matuabom.ai.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.ai.dto.AIRequestDTO;
import org.dallyeo.matuabom.ai.service.AIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class AIController {

    private final AIService aiService;

    @PostMapping("/call")
    public ResponseEntity<?> call(
        @RequestBody AIRequestDTO request
    ) throws GeneralSecurityException, IOException {

        ResponseEntity<?> response = aiService.call(request);
        System.out.println(response.getBody());
        return response;
    }

}
