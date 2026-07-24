package org.dallyeo.matuabom.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * refresh:{userId} 대체. _id = userId.
 * expiresAt: TTL 인덱스(지연 청소) + 애플리케이션 레벨 만료 검사.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refresh_tokens")
public class RefreshTokenDoc {
    @Id
    private String userId;
    private String token;
    private Instant expiresAt;
}
