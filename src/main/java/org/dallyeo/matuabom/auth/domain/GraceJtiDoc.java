package org.dallyeo.matuabom.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * grace_jti:{jti} 대체. _id = 회전된 jti, currentToken = 회전 결과의 현재 refresh token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "grace_jtis")
public class GraceJtiDoc {
    @Id
    private String jti;
    private String currentToken;
    private Instant expiresAt;
}
