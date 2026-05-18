package org.dallyeo.matuabom.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinByInviteCodeRequest {

    @NotBlank(message = "초대 코드는 필수입니다.")
    @Size(min = 8, max = 8, message = "초대 코드는 8자리여야 합니다.")
    private String inviteCode;
}
