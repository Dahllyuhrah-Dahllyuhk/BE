package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.InviteCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InviteCodeRepository extends MongoRepository<InviteCode,String> {
    Optional<InviteCode> findByOwnerUserId(String userId);

    Optional<InviteCode> findByCode(String code);

    Boolean existsByCode(String code);
}
