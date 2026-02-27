package org.dallyeo.matuabom.timetable.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;

@Entity
@Table(
    name = "timetable_items",
    indexes = @Index(name = "idx_timetable_items_timetable_id", columnList = "timetable_id")
)
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class TimetableItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private TimetableEntity timetable;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek day;

    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;
}
