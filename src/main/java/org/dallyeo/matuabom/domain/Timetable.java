package org.dallyeo.matuabom.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "timetables")
public class Timetable {
    @Id
    private String id;

    private String userId;        // 소유자 ID
    private String name;          // 시간표 이름 (예: "시간표 1")

    @Builder.Default
    private boolean isPrimary = false; // 대표 시간표 여부

    @Builder.Default
    private List<TimetableItem> items = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}