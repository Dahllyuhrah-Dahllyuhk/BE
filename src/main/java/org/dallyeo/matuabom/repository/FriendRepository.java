package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FriendRepository extends MongoRepository<Friend,String> {
    Boolean existsByUserId1AndUserId2(String userId1, String userId2);

    List<Friend> findByUserId1OrUserId2(String userId1, String userId2);
}
