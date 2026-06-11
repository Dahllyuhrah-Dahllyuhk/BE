package org.dallyeo.matuabom.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.UserEntity;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class MeDto {
    private String id;
    private String nickname;
    private String profileImageUrl;
    private Instant createdAt;
    /** 현재 약관 버전에 동의했는지 여부 (false면 동의 게이트 노출) */
    private boolean termsAgreed;

    public static MeDto createDto(UserEntity user) {
        String id = user.getMongoId() != null ? user.getMongoId() : String.valueOf(user.getId());
        boolean termsAgreed = user.getTermsAgreedAt() != null
                && UserEntity.CURRENT_TERMS_VERSION.equals(user.getTermsVersion());
        return new MeDto(id, user.getNickname(), user.getProfileImageUrl(), user.getCreatedAt(), termsAgreed);
    }
}
