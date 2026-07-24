package org.dallyeo.matuabom.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.User;

@Getter
@Setter
@AllArgsConstructor
public class FriendDto {
    private String id;
    private String nickname;
    private String profileImageUrl;

    public static FriendDto create(User user) {
        return new FriendDto(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
