package org.dallyeo.matuabom.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * invite:{code} → ownerUserId 캐시 대체. _id = code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invite_cache")
public class InviteCacheDoc {
    @Id
    private String code;
    private String ownerUserId;
    private Instant expiresAt;
}
