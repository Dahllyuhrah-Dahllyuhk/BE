package org.dallyeo.matuabom.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.InviteCodeEntity;

@Getter
@Setter
@AllArgsConstructor
public class InviteCodeResponse {
    private String userId;
    private String code;

    public static InviteCodeResponse from(InviteCodeEntity entity) {
        return new InviteCodeResponse(entity.getOwnerUserId(), entity.getCode());
    }
}