package org.dallyeo.matuabom.domain;

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
    private boolean googleLinked;     // 구글 연동 여부

    @Builder.Default
    private Instant createdAt = Instant.now();

    public void updateKakaoProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateKakaoTokens(String accessToken, Instant accessExp, String refreshToken,  Instant refreshExp
    ) {
        this.kakaoAccessToken = accessToken;
        this.kakaoAccessTokenExpiresAt = accessExp;

        if (refreshToken != null) {
            this.kakaoRefreshToken = refreshToken;
            this.kakaoRefreshTokenExpiresAt = refreshExp;
        }
    }
}
