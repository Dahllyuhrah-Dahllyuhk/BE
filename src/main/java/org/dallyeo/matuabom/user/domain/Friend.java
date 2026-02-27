package org.dallyeo.matuabom.user.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@NoArgsConstructor
@Document(collection = "friends")
public class Friend {
    @Id
    private String id;

    private String userId1;
    private String userId2;

    private Instant createdAt;

    public Friend(String userId1, String userId2) {
        this.userId1 = userId1;
        this.userId2 = userId2;
        this.createdAt = Instant.now();
    }

    public static Friend create(String userId1, String userId2) {
        return new Friend(userId1, userId2);
    }
}
