package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class UpsertKakaoUserDto {
    private final Long kakaoId;
    private final String nickname;
    private final String profileImageUrl;
    private final String kakaoAccessToken;
    private final String kakaoRefreshToken;
    private Instant kakaoAccessTokenExpiresAt;
    private Instant kakaoRefreshTokenExpiresAt;

    public static UpsertKakaoUserDto createDto(Long kakaoId, String nickname, String profileImageUrl, String kakaoAccessToken,
                                               String kakaoRefreshToken, Instant kakaoAccessTokenExpiresAt, Instant kakaoRefreshTokenExpiresAt) {
        return new UpsertKakaoUserDto(kakaoId, nickname, profileImageUrl, kakaoAccessToken, kakaoRefreshToken, kakaoAccessTokenExpiresAt, kakaoRefreshTokenExpiresAt);
    }

}
