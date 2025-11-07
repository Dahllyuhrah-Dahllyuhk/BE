package org.dallyeo.matuabom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserInfo {
    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KakaoAccount {
        private String email;
        @JsonProperty("profile") private KakaoProfile profile;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KakaoProfile {
        private String nickname;
        @JsonProperty("profile_image_url")
        private String profileImageUrl;
    }

    public String getNickname() {
        return kakaoAccount.profile.nickname;
    }
    public String getProfileImageUrl() {
        return kakaoAccount.profile.profileImageUrl;
    }
}
