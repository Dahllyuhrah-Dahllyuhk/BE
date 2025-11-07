package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.domain.User;

@Getter
@Setter
@AllArgsConstructor
public class MeDto {
    private String id;
    private String nickname;
    private String profileImageUrl;

    public static MeDto createDto(User user) {
        return new MeDto(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
