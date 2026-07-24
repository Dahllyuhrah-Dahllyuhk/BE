package org.dallyeo.matuabom.user.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
public class User {

    /** 현재 약관/개인정보처리방침 버전. 개정 시 이 값을 올리면 재동의를 받는다. */
    public static final String CURRENT_TERMS_VERSION = "1";

    @Id
    private String id;
    private Long kakaoId;
    private String nickname;
    private String profileImageUrl;
    private String kakaoAccessToken;
    private String kakaoRefreshToken;

    private Instant kakaoAccessTokenExpiresAt;
    private Instant kakaoRefreshTokenExpiresAt;

    private String googleEmail;
    private boolean googleLinked;

    /** 약관/개인정보 동의 시각 (미동의 시 null) */
    private Instant termsAgreedAt;

    /** 동의한 약관 버전 */
    private String termsVersion;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public void updateKakaoProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateKakaoTokens(String accessToken, Instant accessExp, String refreshToken, Instant refreshExp) {
        this.kakaoAccessToken = accessToken;
        this.kakaoAccessTokenExpiresAt = accessExp;

        if (refreshToken != null) {
            this.kakaoRefreshToken = refreshToken;
            this.kakaoRefreshTokenExpiresAt = refreshExp;
        }
    }
}
