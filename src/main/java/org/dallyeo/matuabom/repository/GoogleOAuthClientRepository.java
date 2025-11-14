package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GoogleOAuthClientRepository
        extends MongoRepository<GoogleOAuthClientEntity, String> {

    Optional<GoogleOAuthClientEntity> findByUserId(String userId);
}
