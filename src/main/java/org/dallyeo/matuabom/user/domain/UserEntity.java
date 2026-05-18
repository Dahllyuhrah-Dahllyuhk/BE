package org.dallyeo.matuabom.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * PostgreSQL 기반 User 엔티티.
 *
 * 민감 토큰(kakaoAccessToken, kakaoRefreshToken, googleRefreshToken)은
 * 이 엔티티에서 제거하고 Redis 또는 별도 암호화 저장소를 사용합니다.
 *
 * 마이그레이션 전략:
 *   1단계(현재): MongoDB User와 공존 — userId(String)로 참조
 *   2단계: MongoDB User 제거 후 이 엔티티를 단일 소스로 사용
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_kakao_id", columnList = "kakao_id", unique = true),
        @Index(name = "idx_users_google_email", columnList = "google_email")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MongoDB의 User._id와 동기화 (마이그레이션 기간 동안 사용) */
    @Column(name = "mongo_id", unique = true)
    private String mongoId;

    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "google_email", length = 200)
    private String googleEmail;

    @Column(name = "google_linked")
    @Builder.Default
    private boolean googleLinked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
