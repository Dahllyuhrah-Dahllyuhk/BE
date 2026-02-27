package org.dallyeo.matuabom.user.repository.jpa;

import org.dallyeo.matuabom.user.domain.InviteCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeJpaRepository extends JpaRepository<InviteCodeEntity, Long> {
    Optional<InviteCodeEntity> findByOwnerUserId(String ownerUserId);
    Optional<InviteCodeEntity> findByCode(String code);
    boolean existsByCode(String code);
}
