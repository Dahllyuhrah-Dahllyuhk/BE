package org.dallyeo.matuabom.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.user.domain.InviteCode;

@Getter
@Setter
@AllArgsConstructor
public class InviteCodeResponse {
    private String userId;
    private String code;

    public static InviteCodeResponse from(InviteCode entity) {
        return new InviteCodeResponse(entity.getOwnerUserId(), entity.getCode());
    }
}