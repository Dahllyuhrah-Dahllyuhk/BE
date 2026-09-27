package org.dallyeo.matuabom.user.repository;

import org.dallyeo.matuabom.user.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByKakaoId(Long kakaoId);

    boolean existsByKakaoId(Long kakaoId);

    List<User> findAllByIdIn(List<String> ids);

    long countByGoogleLinkedTrue();

    long countByCreatedAtAfter(Instant since);

    List<User> findByCreatedAtAfterOrderByCreatedAtAsc(Instant since);
}
