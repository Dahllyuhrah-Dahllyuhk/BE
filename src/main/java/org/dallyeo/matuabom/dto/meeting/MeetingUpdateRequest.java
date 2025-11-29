package org.dallyeo.matuabom.dto.meeting;

import lombok.Value;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Value
public class MeetingUpdateRequest {
    @NotBlank(message = "모임 이름은 필수입니다.")
    String name;

    @Valid
    @NotNull(message = "모임 요구사항은 필수입니다.")
    MeetingRequirementDto requirement;

    @NotNull(message = "초대 사용자 목록은 필수입니다.")
    List<String> invitedUserIds;
}