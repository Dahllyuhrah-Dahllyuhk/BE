package org.dallyeo.matuabom.ai.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.ai.dto.InputCategory;
import org.dallyeo.matuabom.meeting.dto.TimeRangeDto;

@Getter
@Setter
public class AIResponseDTO {

    @JsonProperty("category")
    private InputCategory category;

    @JsonProperty("data")
    @JsonTypeInfo(use = Id.NAME, include = As.EXTERNAL_PROPERTY, property = "category")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = GenerateSchedule.class, name = "?쇱젙?앹꽦"),
        @JsonSubTypes.Type(value = SelectSchedule.class, name = "?쇱젙議고쉶"),
        @JsonSubTypes.Type(value = SelectSchedule.class, name = "?쇱젙??젣"),
        @JsonSubTypes.Type(value = GenerateMeeting.class, name = "紐⑥엫?앹꽦"),
        @JsonSubTypes.Type(value = GetMeeting.class, name = "紐⑥엫議고쉶")
    })
    private Data data;

    public interface Data {}

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

    @Getter
    @Setter
    public static class GenerateMeeting implements Data {
        private String title;
        private String dateRangeStart; // yyyy-MM-dd
        private String dateRangeEnd;   // yyyy-MM-dd
        private Boolean isAllDay;
        private List<TimeRangeDto> timeConstraints;
    }

    @Getter
    @Setter
    public static class GetMeeting implements Data {
        private String title;
        private String dateRangeStart;
        private String dateRangeEnd;
    }

}
