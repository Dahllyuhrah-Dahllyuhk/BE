package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByKakaoId(Long kakaoId);
}
