package org.dallyeo.matuabom.user.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.dto.AddByCodeRequest;
import org.dallyeo.matuabom.user.dto.FriendDto;
import org.dallyeo.matuabom.meeting.dto.InviteCodeResponse;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.user.service.FriendService;
import org.dallyeo.matuabom.meeting.service.InviteCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friends")
public class FriendController {

    private final InviteCodeService inviteCodeService;
    private final FriendService friendService;

    @GetMapping("/invite-code")
    public ResponseEntity<InviteCodeResponse> getMyInviteCode(@AuthenticationPrincipal CustomPrincipal principal) {
        var inviteCode = inviteCodeService.getOrCreateMyInviteCode(principal.getUserId());
        return ResponseEntity.ok(InviteCodeResponse.from(inviteCode));
    }

    @PostMapping("/addFriend")
    public ResponseEntity<?> addByCode(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestBody AddByCodeRequest dto
    ) {
        UserEntity friend = friendService.addFriend(principal.getUserId(), dto.getCode());
        return ResponseEntity.ok().body(FriendDto.create(friend));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> deleteFriend(
            @PathVariable String friendId,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
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
