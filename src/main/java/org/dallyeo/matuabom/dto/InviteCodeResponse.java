package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class InviteCodeResponse {
    private String userId;
    private String code;
}
