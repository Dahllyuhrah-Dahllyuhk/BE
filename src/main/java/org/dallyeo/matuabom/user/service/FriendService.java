package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.Friend;
import org.dallyeo.matuabom.user.domain.InviteCode;
import org.dallyeo.matuabom.user.domain.User;
import org.dallyeo.matuabom.user.repository.FriendRepository;
import org.dallyeo.matuabom.user.repository.InviteCodeRepository;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.dallyeo.matuabom.global.exception.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    public User addFriend(String currentUserId, String code) {
        // 캐시 우선 조회
        String codeOwnerUserId = tokenStore.getCachedInviteOwner(code);

        if (codeOwnerUserId == null) {
            // 캐시 미스 → DB 조회 후 캐싱
            InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                    .orElseThrow(() -> NotFoundException.inviteCode(code));
            codeOwnerUserId = inviteCode.getOwnerUserId();
            tokenStore.cacheInviteCode(code, codeOwnerUserId);
        }

        if (codeOwnerUserId.equals(currentUserId)) {
            throw ConflictException.selfInviteCode();
        }

        // userId 정렬 (중복 방지)
        String u1 = codeOwnerUserId.compareTo(currentUserId) < 0 ? codeOwnerUserId : currentUserId;
        String u2 = codeOwnerUserId.compareTo(currentUserId) < 0 ? currentUserId : codeOwnerUserId;

        if (Boolean.TRUE.equals(friendRepository.existsByUserId1AndUserId2(u1, u2))) {
            throw ConflictException.alreadyFriend();
        }

        friendRepository.save(Friend.create(codeOwnerUserId, currentUserId));

        final String ownerUserId = codeOwnerUserId;
        return userRepository.findById(ownerUserId)
                .orElseThrow(() -> NotFoundException.user(ownerUserId));
    }

    public List<User> getFriendsList(String userId) {
        List<Friend> relations = friendRepository.findByUserId1OrUserId2(userId, userId);
        List<String> friendIds = relations.stream()
                .map(f -> f.getUserId1().equals(userId) ? f.getUserId2() : f.getUserId1())
                .distinct()
                .collect(Collectors.toList());

        if (friendIds.isEmpty()) return List.of();

        return userRepository.findAllByIdIn(friendIds);
    }

    public User addFriendByUserId(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw ConflictException.selfFriend();
        }

        String u1 = currentUserId.compareTo(targetUserId) < 0 ? currentUserId : targetUserId;
        String u2 = currentUserId.compareTo(targetUserId) < 0 ? targetUserId : currentUserId;

        if (Boolean.TRUE.equals(friendRepository.existsByUserId1AndUserId2(u1, u2))) {
            throw ConflictException.alreadyFriend();
        }

        friendRepository.save(Friend.create(currentUserId, targetUserId));

        return userRepository.findById(targetUserId)
                .orElseThrow(() -> NotFoundException.user(targetUserId));
    }

    public void deleteFriend(String currentUserId, String targetUserId) {
        String u1 = currentUserId.compareTo(targetUserId) < 0 ? currentUserId : targetUserId;
        String u2 = currentUserId.compareTo(targetUserId) < 0 ? targetUserId : currentUserId;

        Friend relation = friendRepository.findByUserId1AndUserId2(u1, u2)
                .orElseThrow(() -> NotFoundException.friend());

        friendRepository.delete(relation);

        // [정책] 친구 삭제 시 기존 모임의 MeetingParticipant 데이터는 유지됩니다.
        // 이미 확정(CONFIRMED)되거나 진행 중인 모임의 참여 이력을 보존하는 것이 의도된 동작입니다.
        // 모임에서도 해당 사용자를 제거하려면 모임 호스트가 직접 모임을 수정해야 합니다.
    }
}
