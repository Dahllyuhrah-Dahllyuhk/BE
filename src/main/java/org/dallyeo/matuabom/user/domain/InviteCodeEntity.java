package org.dallyeo.matuabom.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * PostgreSQL 기반 InviteCode 엔티티.
 *
 * 조회 패턴: code → ownerUserId (단방향, 반복 조회 多)
 * → Redis에 invite:{code} = ownerUserId 형태로 캐싱 권장.
 *   캐시 미스 시 이 테이블에서 조회.
 */
@Entity
@Table(
    name = "invite_codes",
    indexes = {
        @Index(name = "idx_invite_codes_code", columnList = "code", unique = true),
        @Index(name = "idx_invite_codes_owner", columnList = "owner_user_id", unique = true)
    }
)
@Getter
@NoArgsConstructor
public class InviteCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "owner_user_id", nullable = false, unique = true, length = 50)
    private String ownerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InviteCodeEntity(String code, String ownerUserId) {
        this.code = code;
        this.ownerUserId = ownerUserId;
        this.createdAt = Instant.now();
    }
}
