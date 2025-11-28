package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import org.springframework.data.mongodb.repository.Query;

public interface MeetingRepository extends MongoRepository<Meeting, String> {
    List<Meeting> findAllByHostUserIdOrParticipantsUserId(String hostId, String participantId);

    @Query("{ " +
        "  $and: [ " +
        "    { $or: [ { 'hostUserId': ?0 }, { 'participants.userId': ?0 } ] }, " +
        "    { 'name': { $regex: ?1, $options: 'i' } } " +
        "  ] " +
        "}")
    List<Meeting> searchByTitle(String userId, String keyword);

    @Query("{ " +
        "  $and: [ " +
        "    { $or: [ { 'hostUserId': ?0 }, { 'participants.userId': ?0 } ] }, " +
        "    { 'name': { $regex: ?1, $options: 'i' } }, " +
        "    { 'requirement.dateRangeStart': { $lte: ?3 } }, " +
        "    { 'requirement.dateRangeEnd':   { $gte: ?2 } } " +
        "  ] " +
        "}")
    List<Meeting> searchByTitleAndDateRange(String userId, String keyword, String queryStart, String queryEnd);

    List<Meeting> findByStatusAndParticipantsUserId(String status, String userId);


}
