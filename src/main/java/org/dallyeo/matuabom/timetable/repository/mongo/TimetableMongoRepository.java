package org.dallyeo.matuabom.timetable.repository.mongo;

import org.dallyeo.matuabom.timetable.domain.Timetable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TimetableMongoRepository extends MongoRepository<Timetable, String> {
    List<Timetable> findAllByUserId(String userId);
    Timetable findByUserIdAndIsPrimaryTrue(String userId);
}
