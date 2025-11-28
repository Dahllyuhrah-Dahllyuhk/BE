package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.domain.User;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class MeDto {
    private String id;
    private String nickname;
    private String profileImageUrl;
    private Instant createdAt;

    public static MeDto createDto(User user) {
        return new MeDto(user.getId(), user.getNickname(), user.getProfileImageUrl(),user.getCreatedAt());
    }
}
