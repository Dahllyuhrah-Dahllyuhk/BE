package org.dallyeo.matuabom.user.repository;

import org.dallyeo.matuabom.user.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByKakaoId(Long kakaoId);
}
