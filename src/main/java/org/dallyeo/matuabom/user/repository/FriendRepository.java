package org.dallyeo.matuabom.user.repository;

import org.dallyeo.matuabom.user.domain.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends MongoRepository<Friend, String> {
    Boolean existsByUserId1AndUserId2(String userId1, String userId2);

    List<Friend> findByUserId1OrUserId2(String userId1, String userId2);

    Optional<Friend> findByUserId1AndUserId2(String userId1, String userId2);
}
