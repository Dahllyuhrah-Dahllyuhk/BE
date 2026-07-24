package org.dallyeo.matuabom.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * auth_code:{code} → accessToken 1회용 임시 코드 대체. _id = code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "auth_codes")
public class AuthCodeDoc {
    @Id
    private String code;
    private String accessToken;
    private Instant expiresAt;
}
