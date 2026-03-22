package org.dallyeo.matuabom.user.repository.jpa;

import org.dallyeo.matuabom.user.domain.FriendEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendJpaRepository extends JpaRepository<FriendEntity, Long> {

    boolean existsByUserId1AndUserId2(String userId1, String userId2);

    @Query("SELECT f FROM FriendEntity f WHERE f.userId1 = :userId OR f.userId2 = :userId")
    List<FriendEntity> findAllByUserId(@Param("userId") String userId);

    Optional<FriendEntity> findByUserId1AndUserId2(String userId1, String userId2);

    void deleteAllByUserId1OrUserId2(String userId1, String userId2);
}
