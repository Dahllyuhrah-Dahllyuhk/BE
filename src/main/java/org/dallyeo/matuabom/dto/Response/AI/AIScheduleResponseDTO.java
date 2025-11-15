package org.dallyeo.matuabom.dto.Response.AI;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.dallyeo.matuabom.dto.Response.InputCategory;

@Getter
@Setter
public class AIScheduleResponseDTO {

    @JsonProperty("category")
    InputCategory category;

    @JsonProperty("data")
    Data data;

    @Getter
    @Setter
    public static class Data {

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("start")
        private Long start;

        @JsonProperty("end")
        private Long end;

        @JsonProperty("allDay")
        private Boolean allDay;

        @JsonProperty("timeZone")
        private String timeZone;

    }

}
