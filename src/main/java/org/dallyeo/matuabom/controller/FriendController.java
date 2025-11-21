package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.*;
import org.dallyeo.matuabom.security.CustomPrincipal; // ✅ 임포트
import org.dallyeo.matuabom.service.FriendService;
import org.dallyeo.matuabom.service.InviteCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friends")
public class FriendController {
    // JwtUtil 의존성 제거됨
    private final InviteCodeService inviteCodeService;
    private final FriendService friendService;

    @GetMapping("/invite-code")
    public ResponseEntity<InviteCodeResponse> getMyInviteCode(@AuthenticationPrincipal CustomPrincipal principal) {
        // principal.getUserId()로 바로 사용
        var inviteCode = inviteCodeService.getOrCreateMyInviteCode(principal.getUserId());
        return ResponseEntity.ok().body(new InviteCodeResponse(inviteCode.getOwnerUserId(), inviteCode.getCode()));
    }

    @PostMapping("/addFriend")
    public ResponseEntity<?> addByCode(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestBody AddByCodeRequest dto
    ) {
        // try-catch 등 로직은 그대로 유지...
        User friend = friendService.addFriend(principal.getUserId(), dto.getCode());
        return ResponseEntity.ok().body(FriendDto.create(friend));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> deleteFriend(@PathVariable String friendId, @AuthenticationPrincipal CustomPrincipal principal) {
        friendService.deleteFriend(principal.getUserId(), friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendDto>> getMyFriends(@AuthenticationPrincipal CustomPrincipal principal) {
        List<FriendDto> friends = friendService.getFriendsList(principal.getUserId())
                .stream()
                .map(FriendDto::create)
                .toList();
        return ResponseEntity.ok().body(friends);
    }
}