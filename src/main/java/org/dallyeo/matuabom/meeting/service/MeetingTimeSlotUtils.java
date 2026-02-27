package org.dallyeo.matuabom.meeting.service;

import org.dallyeo.matuabom.meeting.domain.ParticipantTimeStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 */
public class MeetingTimeSlotUtils {

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    /**
     */
    public static Set<Integer> convertRangeToSlots(Instant start, Instant end) {
        Set<Integer> slots = new HashSet<>();

        ZonedDateTime zdtStart = start.atZone(ZONE_UTC);
        ZonedDateTime zdtEnd = end.atZone(ZONE_UTC);

        LocalDate date = zdtStart.toLocalDate();

        ZonedDateTime current = zdtStart.withMinute(0).withSecond(0).withNano(0);

        while (current.isBefore(zdtEnd)) {
            if (!current.toLocalDate().equals(date)) {
            }

            Instant slotStartInstant = current.toInstant();
            Instant slotEndInstant = current.plusHours(1).toInstant();

            if (slotStartInstant.isBefore(end) && slotEndInstant.isAfter(start)) {
                slots.add(current.getHour());
            }

            current = current.plusHours(1);
        }

        return slots;
    }

    /**
     */
    public static List<ParticipantTimeStatus> convertSlotsToRanges(
        LocalDate date,
        Set<Integer> slots,
        String status
    ) {
        if (slots.isEmpty()) return Collections.emptyList();

        List<Integer> sortedSlots = new ArrayList<>(slots);
        Collections.sort(sortedSlots);

        List<ParticipantTimeStatus> ranges = new ArrayList<>();


        return ranges;
    }
}
