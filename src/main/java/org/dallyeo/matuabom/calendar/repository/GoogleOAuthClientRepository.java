package org.dallyeo.matuabom.calendar.repository;

import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GoogleOAuthClientRepository extends MongoRepository<GoogleOAuthClientEntity, String> {

    Optional<GoogleOAuthClientEntity> findByUserId(String userId);

    Optional<GoogleOAuthClientEntity> findByWatchChannelId(String watchChannelId);

    void deleteByUserId(String userId);
}
