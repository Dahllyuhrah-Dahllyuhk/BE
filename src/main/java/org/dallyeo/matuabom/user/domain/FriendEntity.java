package org.dallyeo.matuabom.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * PostgreSQL 기반 Friend 관계 엔티티.
 * userId1 < userId2 정렬 보장 (중복 방지).
 */
@Entity
@Table(
    name = "friends",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_friends_user1_user2",
        columnNames = {"user_id_1", "user_id_2"}
    ),
    indexes = {
        @Index(name = "idx_friends_user1", columnList = "user_id_1"),
        @Index(name = "idx_friends_user2", columnList = "user_id_2")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FriendEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id_1", nullable = false, length = 50)
    private String userId1;

    @Column(name = "user_id_2", nullable = false, length = 50)
    private String userId2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FriendEntity create(String a, String b) {
        String u1 = a.compareTo(b) < 0 ? a : b;
        String u2 = a.compareTo(b) < 0 ? b : a;
        FriendEntity e = new FriendEntity();
        e.userId1 = u1;
        e.userId2 = u2;
        e.createdAt = Instant.now();
        return e;
    }
}
