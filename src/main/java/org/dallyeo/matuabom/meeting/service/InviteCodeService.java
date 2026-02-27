package org.dallyeo.matuabom.meeting.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.InviteCodeEntity;
import org.dallyeo.matuabom.user.repository.jpa.InviteCodeJpaRepository;
import org.dallyeo.matuabom.global.util.InviteCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private final InviteCodeJpaRepository inviteCodeJpaRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final TokenStore tokenStore;

    @Transactional
    public InviteCodeEntity getOrCreateMyInviteCode(String userId) {
        return inviteCodeJpaRepository.findByOwnerUserId(userId)
                .orElseGet(() -> {
                    String code = generateUniqueCode(9);
                    InviteCodeEntity entity = new InviteCodeEntity(code, userId);
                    InviteCodeEntity saved = inviteCodeJpaRepository.save(entity);
                    // 신규 생성 즉시 Redis에 캐싱
                    tokenStore.cacheInviteCode(code, userId);
                    return saved;
                });
    }

    private String generateUniqueCode(int length) {
        while (true) {
            String code = inviteCodeGenerator.generateRandomCode(length);
            if (!inviteCodeJpaRepository.existsByCode(code)) {
                return code;
            }
        }
    }
}
