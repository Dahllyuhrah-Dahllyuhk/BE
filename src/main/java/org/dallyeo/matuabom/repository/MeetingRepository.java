package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MeetingRepository extends MongoRepository<Meeting, String> {
    List<Meeting> findAllByHostUserIdOrParticipantsUserId(String hostId, String participantId);
}
