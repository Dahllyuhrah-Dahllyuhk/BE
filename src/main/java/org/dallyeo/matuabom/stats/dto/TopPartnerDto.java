package org.dallyeo.matuabom.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TopPartnerDto {
    private String userId;
    private String name;
    private long meetingCount;
}