package org.dallyeo.matuabom.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.User;

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

    public static MeDto createDto(User user) {
        boolean termsAgreed = user.getTermsAgreedAt() != null
                && User.CURRENT_TERMS_VERSION.equals(user.getTermsVersion());
        return new MeDto(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getCreatedAt(), termsAgreed);
    }
}
