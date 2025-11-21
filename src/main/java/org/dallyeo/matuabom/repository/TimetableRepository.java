package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.Timetable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TimetableRepository extends MongoRepository<Timetable, String> {
    // 특정 유저의 모든 시간표 조회
    List<Timetable> findAllByUserId(String userId);

    // 특정 유저의 대표 시간표 조회
    Timetable findByUserIdAndIsPrimaryTrue(String userId);
}
