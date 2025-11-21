package org.dallyeo.matuabom.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.Request.AIRequestDTO;
import org.dallyeo.matuabom.service.AIService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    //사용자 질의를 받아서 FastAPI 로 전송
    @PostMapping("/call")
    public void call(
        @RequestBody AIRequestDTO request
    ) throws GeneralSecurityException, IOException {
        aiService.call(request);
    }

}
