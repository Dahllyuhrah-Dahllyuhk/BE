package org.dallyeo.matuabom.user.repository.jpa;

import org.dallyeo.matuabom.user.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByKakaoId(Long kakaoId);
    Optional<UserEntity> findByMongoId(String mongoId);
    boolean existsByKakaoId(Long kakaoId);

    @Query("SELECT u FROM UserEntity u WHERE u.mongoId IN :mongoIds")
    List<UserEntity> findAllByMongoIdIn(@Param("mongoIds") List<String> mongoIds);
}
