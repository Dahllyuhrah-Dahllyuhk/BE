package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TopPartnerDto {
    private String userId;
    private String name;
    private long meetingCount;
}
