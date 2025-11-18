package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.InviteCode;
import org.dallyeo.matuabom.repository.InviteCodeRepository;
import org.dallyeo.matuabom.util.InviteCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InviteCodeService {
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public InviteCode getOrCreateMyInviteCode(String userId) {
        return inviteCodeRepository
                .findByOwnerUserId(userId)
                .orElseGet(()->{
                    String code = generateUniqueCode(9); //코드 길이는 여기서 수정 가능.
                    InviteCode inviteCode = InviteCode.create(code, userId);
                    return inviteCodeRepository.save(inviteCode);
                });
    }

    public String generateUniqueCode(int length) {
        while(true){
            String code = inviteCodeGenerator.generateRandomCode(length);
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
    }
}
