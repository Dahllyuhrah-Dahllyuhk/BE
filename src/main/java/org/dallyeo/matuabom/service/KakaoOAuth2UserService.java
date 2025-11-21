package org.dallyeo.matuabom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.KakaoUserInfo;
import org.dallyeo.matuabom.repository.UserRepository;
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

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = super.loadUser(userRequest);
        Map<String, Object> attributes = kakaoUser.getAttributes();

        ObjectMapper objectMapper = new ObjectMapper();

        KakaoUserInfo userInfo = objectMapper.convertValue(attributes, KakaoUserInfo.class);
        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        userRepository.findByKakaoId(kakaoId)
                .map(u -> { u.updateKakaoProfile(nickname, profileImageUrl); return userRepository.save(u); })
                .orElseGet(() -> userRepository.save(
                        User.builder().kakaoId(kakaoId).nickname(nickname).profileImageUrl(profileImageUrl).build()
                ));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new DefaultOAuth2User(authorities, attributes, "id");
    }
}

