package org.dallyeo.matuabom.timetable.domain;

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

    private String userId;
    private String name;

    @Builder.Default
    private boolean isPrimary = false;

    @Builder.Default
    private List<TimetableItem> items = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
