package org.dallyeo.matuabom.timetable.repository.jpa;

import org.dallyeo.matuabom.timetable.domain.TimetableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableJpaRepository extends JpaRepository<TimetableEntity, Long> {
    List<TimetableEntity> findAllByUserId(String userId);
    Optional<TimetableEntity> findByUserIdAndIsPrimaryTrue(String userId);
}
