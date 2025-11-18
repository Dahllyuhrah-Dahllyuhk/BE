package org.dallyeo.matuabom.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@NoArgsConstructor
@Document(collection = "invite_codes")
public class InviteCode {
    @Id
    private String id;

    private String code;
    private String ownerUserId;

    private Instant createdAt;

    public InviteCode(String code, String ownerUserId) {
        this.code = code;
        this.ownerUserId = ownerUserId;
        this.createdAt = Instant.now();
    }

    public static InviteCode create(String code, String ownerUserId) {
        return new InviteCode(code, ownerUserId);
    }
}
