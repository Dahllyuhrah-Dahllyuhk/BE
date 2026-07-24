package org.dallyeo.matuabom.meeting.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.InviteCode;
import org.dallyeo.matuabom.user.repository.InviteCodeRepository;
import org.dallyeo.matuabom.global.util.InviteCodeGenerator;
import org.dallyeo.matuabom.global.exception.ConflictException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final TokenStore tokenStore;

    public InviteCode getOrCreateMyInviteCode(String userId) {
        return inviteCodeRepository.findByOwnerUserId(userId)
                .orElseGet(() -> {
                    String code = generateUniqueCode(9);
                    InviteCode saved = inviteCodeRepository.save(InviteCode.create(code, userId));
                    // 신규 생성 즉시 캐싱
                    tokenStore.cacheInviteCode(code, userId);
                    return saved;
                });
    }

    private String generateUniqueCode(int length) {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            String code = inviteCodeGenerator.generateRandomCode(length);
            if (!Boolean.TRUE.equals(inviteCodeRepository.existsByCode(code))) {
                return code;
            }
        }
        throw ConflictException.inviteCodeGenerationFailed();
    }
}
