package org.dallyeo.matuabom.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.UserEntity;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class MeDto {
    private String id;
    private String nickname;
    private String profileImageUrl;
    private Instant createdAt;

    public static MeDto createDto(UserEntity user) {
        String id = user.getMongoId() != null ? user.getMongoId() : String.valueOf(user.getId());
        return new MeDto(id, user.getNickname(), user.getProfileImageUrl(), user.getCreatedAt());
    }
}
