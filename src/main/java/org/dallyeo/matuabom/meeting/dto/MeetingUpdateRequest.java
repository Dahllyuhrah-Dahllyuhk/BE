package org.dallyeo.matuabom.meeting.dto;

import lombok.Value;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Value
public class MeetingUpdateRequest {
    @NotBlank(message = "紐⑥엫 ?대쫫? ?꾩닔?낅땲??")
    String name;

    @Valid
    @NotNull(message = "紐⑥엫 ?붽뎄?ы빆? ?꾩닔?낅땲??")
    MeetingRequirementDto requirement;

    @NotNull(message = "珥덈? ?ъ슜??紐⑸줉? ?꾩닔?낅땲??")
    List<String> invitedUserIds;
}