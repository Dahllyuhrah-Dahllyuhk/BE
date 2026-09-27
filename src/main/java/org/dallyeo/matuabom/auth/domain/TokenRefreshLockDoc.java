package org.dallyeo.matuabom.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * token_refresh_lock:{userId} 분산락 대체.
 * _id 유니크 insert로 락 점유(DuplicateKey=획득 실패). expiresAt 만료 시 steal 허용.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "token_refresh_locks")
public class TokenRefreshLockDoc {
    @Id
    private String id;
    private Instant expiresAt;
}
