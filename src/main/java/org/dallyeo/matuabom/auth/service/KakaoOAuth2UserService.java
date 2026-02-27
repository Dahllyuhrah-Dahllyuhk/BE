package org.dallyeo.matuabom.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.auth.dto.KakaoUserInfo;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private final UserJpaRepository userJpaRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = super.loadUser(userRequest);
        Map<String, Object> attributes = kakaoUser.getAttributes();

        KakaoUserInfo userInfo = new ObjectMapper().convertValue(attributes, KakaoUserInfo.class);
        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        userJpaRepository.findByKakaoId(kakaoId)
                .map(u -> {
                    u.setNickname(nickname);
                    u.setProfileImageUrl(profileImageUrl);
                    return userJpaRepository.save(u);
                })
                .orElseGet(() -> {
                    UserEntity saved = userJpaRepository.save(
                            UserEntity.builder()
                                    .kakaoId(kakaoId)
                                    .nickname(nickname)
                                    .profileImageUrl(profileImageUrl)
                                    .build()
                    );
                    // mongoId를 Long PK의 문자열로 초기화
                    saved.setMongoId(String.valueOf(saved.getId()));
                    return userJpaRepository.save(saved);
                });

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new DefaultOAuth2User(authorities, attributes, "id");
    }
}
