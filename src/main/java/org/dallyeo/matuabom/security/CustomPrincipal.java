package org.dallyeo.matuabom.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomPrincipal {
    // 가장 중요한 식별자 (DB의 _id)
    private String userId;

    // (선택사항) 추후 확장성을 위해 이메일이나 권한(Role)을 넣을 수도 있음
    // private String email;
    // private String role;
}