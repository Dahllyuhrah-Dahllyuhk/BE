package org.dallyeo.matuabom.domain;

import lombok.*;
import java.time.DayOfWeek;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimetableItem {
    // MongoDB 내장 객체라도 관리를 위해 ID를 두는 것이 좋습니다. (UUID 등)
    private String id;

    private String title;       // 수업명 (ex: 자료구조)
    private String color;       // 색상 코드 (ex: #FF5733 or blue-500)

    private DayOfWeek day;      // 요일 (JAVA DayOfWeek Enum 사용 권장)

    // "HH:mm" 형식으로 저장 (프론트와 통신 편의성)
    private String startTime;   // "09:00"
    private String endTime;     // "10:30"
}