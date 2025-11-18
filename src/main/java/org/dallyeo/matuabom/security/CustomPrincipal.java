package org.dallyeo.matuabom.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CustomPrincipal {
    private String userId;
    private String accessToken;
}
