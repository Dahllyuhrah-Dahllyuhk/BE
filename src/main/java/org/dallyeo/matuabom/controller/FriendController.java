package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.InviteCode;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.AddByCodeRequest;
import org.dallyeo.matuabom.dto.FriendDto;
import org.dallyeo.matuabom.dto.InviteCodeResponse;
import org.dallyeo.matuabom.service.FriendService;
import org.dallyeo.matuabom.service.InviteCodeService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friends")
public class FriendController {
    private final JwtUtil jwtUtil;
    private final InviteCodeService inviteCodeService;
    private final FriendService friendService;

    public String getUserIdFromToken(String token) {
        return jwtUtil.validateAndGetSub(token);
    }


    @GetMapping("/invite-code")
    public ResponseEntity<InviteCodeResponse> getMyInviteCode(@CookieValue(value = "ACCESS_TOKEN",required = false)String token) {
        if(token == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String userId = getUserIdFromToken(token);
        InviteCode inviteCode = inviteCodeService.getOrCreateMyInviteCode(userId);

        return ResponseEntity.ok().body(new InviteCodeResponse(inviteCode.getOwnerUserId(), inviteCode.getCode()));
    }

    @PostMapping("/addFriend")
    public ResponseEntity<?> addByCode(@CookieValue(value = "ACCESS_TOKEN",required = false)String token,@RequestBody AddByCodeRequest dto) {
        if(token == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String userId = getUserIdFromToken(token);
        try {
            User friend = friendService.addFriend(userId, dto.getCode());
            return ResponseEntity.ok().body(FriendDto.create(friend)); //친구 추가된 친구 유저의 정보 반환
        } catch (IllegalArgumentException e) {
            //초대코드가 없거나 잘못된 입력
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            //서비스단에서 넘어온 상태에 따른 실패
            if ("이미 친구입니다.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            } else if ("자기 자신의 초대코드는 사용할 수 없습니다.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            } else if ("친구 유저 정보가 존재하지 않습니다.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            //이 외에는 400으로 처리
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

    }

    @GetMapping
    public ResponseEntity<List<FriendDto>> getMyFriends(@CookieValue(value = "ACCESS_TOKEN",required = false)String token) {
        if(token == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String userId = getUserIdFromToken(token);
        List<FriendDto> friends = friendService.getFriendsList(userId)
                .stream()
                .map(FriendDto::create)
                .toList();
        return ResponseEntity.ok().body(friends);
    }
}
