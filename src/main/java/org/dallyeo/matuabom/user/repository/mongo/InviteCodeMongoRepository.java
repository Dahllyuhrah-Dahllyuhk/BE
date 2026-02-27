package org.dallyeo.matuabom.user.repository.mongo;

import org.dallyeo.matuabom.user.domain.InviteCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InviteCodeMongoRepository extends MongoRepository<InviteCode, String> {
    Optional<InviteCode> findByOwnerUserId(String userId);
    Optional<InviteCode> findByCode(String code);
    Boolean existsByCode(String code);
}
