package org.dallyeo.matuabom.timetable.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.timetable.domain.TimetableEntity;
import org.dallyeo.matuabom.timetable.dto.TimetableItemRequest;
import org.dallyeo.matuabom.timetable.dto.TimetableRequest;
import org.dallyeo.matuabom.timetable.service.TimetableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @PostMapping
    public ResponseEntity<TimetableEntity> createTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestBody TimetableRequest request
    ) {
        return ResponseEntity.ok(timetableService.createTimetable(principal.getUserId(), request.getName()));
    }

    @GetMapping
    public ResponseEntity<List<TimetableEntity>> getMyTimetables(
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        return ResponseEntity.ok(timetableService.getMyTimetables(principal.getUserId()));
    }

    @GetMapping("/{timetableId}")
    public ResponseEntity<TimetableEntity> getTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId
    ) {
        return ResponseEntity.ok(timetableService.getTimetable(principal.getUserId(), timetableId));
    }

    @PutMapping("/{timetableId}")
    public ResponseEntity<TimetableEntity> updateTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId,
            @RequestBody TimetableRequest request
    ) {
        return ResponseEntity.ok(
                timetableService.updateTimetableName(principal.getUserId(), timetableId, request.getName()));
    }

    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId
    ) {
        timetableService.deleteTimetable(principal.getUserId(), timetableId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{timetableId}/items")
    public ResponseEntity<TimetableEntity> addItem(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId,
            @RequestBody TimetableItemRequest dto
    ) {
        return ResponseEntity.ok(
                timetableService.addItemToTimetable(principal.getUserId(), timetableId, dto));
    }

    @PutMapping("/{timetableId}/items/{itemId}")
    public ResponseEntity<TimetableEntity> updateItem(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId,
            @PathVariable Long itemId,
            @RequestBody TimetableItemRequest dto
    ) {
        return ResponseEntity.ok(
                timetableService.updateTimetableItem(principal.getUserId(), timetableId, itemId, dto));
    }

    @DeleteMapping("/{timetableId}/items/{itemId}")
    public ResponseEntity<TimetableEntity> deleteItem(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long timetableId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(
                timetableService.deleteTimetableItem(principal.getUserId(), timetableId, itemId));
    }
}
