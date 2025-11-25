package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.Timetable;
import org.dallyeo.matuabom.dto.TimetableRequest;
import org.dallyeo.matuabom.dto.TimetableItemRequest;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.dallyeo.matuabom.service.TimetableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    // 시간표 생성
    @PostMapping
    public ResponseEntity<Timetable> createTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestBody TimetableRequest request
    ) {
        return ResponseEntity.ok(
            timetableService.createTimetable(principal.getUserId(), request.getName())
        );
    }

    //시간표 수정
    @PutMapping("/{timetableId}")
        public ResponseEntity<Timetable> updateTimetable(
                @AuthenticationPrincipal CustomPrincipal principal,
                @PathVariable String timetableId,
                @RequestBody TimetableRequest request // Body에서 {name} 받기
        ) {
            return ResponseEntity.ok(
                timetableService.updateTimetableName(
                    principal.getUserId(),
                    timetableId,
                    request.getName()
                )
            );
        }
    //시간표 삭제
    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable String timetableId
    ) {
        timetableService.deleteTimetable(principal.getUserId(), timetableId);
        return ResponseEntity.noContent().build();
    }

    // 수업 추가 (POST /api/timetables/{id}/items)
    @PostMapping("/{timetableId}/items")
    public ResponseEntity<Timetable> addTimetableItem(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable String timetableId,
            @RequestBody TimetableItemRequest dto
    ) {
        return ResponseEntity.ok(
            timetableService.addItemToTimetable(principal.getUserId(), timetableId, dto)
        );
    }

  @GetMapping
      public ResponseEntity<List<Timetable>> getMyTimetables(@AuthenticationPrincipal CustomPrincipal principal) {
          return ResponseEntity.ok(timetableService.getMyTimetables(principal.getUserId()));
      }

      // 특정 시간표 상세 조회 (아이템 포함)
      @GetMapping("/{timetableId}")
      public ResponseEntity<Timetable> getTimetable(
              @AuthenticationPrincipal CustomPrincipal principal,
              @PathVariable String timetableId
      ) {
          return ResponseEntity.ok(timetableService.getTimetable(principal.getUserId(), timetableId));
      }

      // 수업 수정 (PUT /api/timetables/{tid}/items/{iid})
      @PutMapping("/{timetableId}/items/{itemId}")
      public ResponseEntity<Timetable> updateItem(
              @AuthenticationPrincipal CustomPrincipal principal,
              @PathVariable String timetableId,
              @PathVariable String itemId,
              @RequestBody TimetableItemRequest dto
      ) {
          return ResponseEntity.ok(
              timetableService.updateTimetableItem(principal.getUserId(), timetableId, itemId, dto)
          );
      }

      // 수업 삭제 (DELETE /api/timetables/{tid}/items/{iid})
      @DeleteMapping("/{timetableId}/items/{itemId}")
      public ResponseEntity<Timetable> deleteItem(
              @AuthenticationPrincipal CustomPrincipal principal,
              @PathVariable String timetableId,
              @PathVariable String itemId
      ) {
          return ResponseEntity.ok(
              timetableService.deleteTimetableItem(principal.getUserId(), timetableId, itemId)
          );
      }
}
