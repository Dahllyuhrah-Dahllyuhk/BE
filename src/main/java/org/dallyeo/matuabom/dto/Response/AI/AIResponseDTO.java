package org.dallyeo.matuabom.dto.Response.AI;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.dto.Response.InputCategory;
import org.dallyeo.matuabom.dto.meeting.TimeRangeDto;

@Getter
@Setter
public class AIResponseDTO {

    @JsonProperty("category")
    private InputCategory category;

    // category 필드의 값(이름)을 보고 Data의 구현체를 결정합니다.
    @JsonProperty("data")
    @JsonTypeInfo(use = Id.NAME, include = As.EXTERNAL_PROPERTY, property = "category")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = GenerateSchedule.class, name = "일정생성"),
        @JsonSubTypes.Type(value = SelectSchedule.class, name = "일정조회"),
        @JsonSubTypes.Type(value = SelectSchedule.class, name = "일정삭제"),
        @JsonSubTypes.Type(value = GenerateMeeting.class, name = "모임생성"),
        @JsonSubTypes.Type(value = GetMeeting.class, name = "모임조회")
    })
    private Data data;

    public interface Data {}

    // 카테고리가 일정생성인 경우
    @Getter @Setter
    public static class GenerateSchedule implements Data {
        private String title;
        private String description;
        private String start;
        private String end;
        private Boolean allDay;
        private String timeZone;
    }

    @Getter @Setter
    public static class SelectSchedule implements Data {
        private Long start;
        private Long end;
        private String keyword;
    }

    // 카테고리가 모임생성
    @Getter
    @Setter
    public static class GenerateMeeting implements Data {
        private String title;
        private String dateRangeStart; // yyyy-MM-dd
        private String dateRangeEnd;   // yyyy-MM-dd
        private Boolean isAllDay;
        // 💡 시간 제약 (isAllDay가 false일 때만 사용)
        private List<TimeRangeDto> timeConstraints;
    }

    @Getter
    @Setter
    public static class GetMeeting implements Data {
        private String title;
        private String dateRangeStart; // yyyy-MM-dd (없으면 null)
        private String dateRangeEnd;   // yyyy-MM-dd (없으면 null)
    }

}