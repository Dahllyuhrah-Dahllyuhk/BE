package org.dallyeo.matuabom.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.UserEntity;

@Getter
@Setter
@AllArgsConstructor
public class FriendDto {
    private String id;
    private String nickname;
    private String profileImageUrl;

    public static FriendDto create(UserEntity user) {
        String id = user.getMongoId() != null ? user.getMongoId() : String.valueOf(user.getId());
        return new FriendDto(id, user.getNickname(), user.getProfileImageUrl());
    }
}
