package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.FriendEntity;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.repository.jpa.FriendJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.InviteCodeJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.dallyeo.matuabom.user.domain.InviteCodeEntity;
import org.dallyeo.matuabom.global.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendJpaRepository friendJpaRepository;
    private final InviteCodeJpaRepository inviteCodeJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final TokenStore tokenStore;

    @Transactional
    public UserEntity addFriend(String currentUserId, String code) {
        // Redis 캐시 우선 조회
        String codeOwnerUserId = tokenStore.getCachedInviteOwner(code);

        if (codeOwnerUserId == null) {
            // 캐시 미스 → DB 조회 후 캐싱
            InviteCodeEntity inviteCode = inviteCodeJpaRepository.findByCode(code)
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

        if (friendJpaRepository.existsByUserId1AndUserId2(u1, u2)) {
            throw ConflictException.alreadyFriend();
        }

        friendJpaRepository.save(FriendEntity.create(codeOwnerUserId, currentUserId));

        final String ownerUserId = codeOwnerUserId;
        return userJpaRepository.findByMongoId(ownerUserId)
                .orElseThrow(() -> NotFoundException.friend());
    }

    @Transactional(readOnly = true)
    public List<UserEntity> getFriendsList(String userId) {
        List<FriendEntity> relations = friendJpaRepository.findAllByUserId(userId);
        List<String> friendIds = relations.stream()
                .map(f -> f.getUserId1().equals(userId) ? f.getUserId2() : f.getUserId1())
                .distinct()
                .collect(Collectors.toList());

        if (friendIds.isEmpty()) return List.of();

        return userJpaRepository.findAllByMongoIdIn(friendIds);
    }

    @Transactional
    public UserEntity addFriendByUserId(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw ConflictException.selfFriend();
        }

        String u1 = currentUserId.compareTo(targetUserId) < 0 ? currentUserId : targetUserId;
        String u2 = currentUserId.compareTo(targetUserId) < 0 ? targetUserId : currentUserId;

        if (friendJpaRepository.existsByUserId1AndUserId2(u1, u2)) {
            throw ConflictException.alreadyFriend();
        }

        friendJpaRepository.save(FriendEntity.create(currentUserId, targetUserId));

        return userJpaRepository.findByMongoId(targetUserId)
                .orElseThrow(() -> NotFoundException.user(targetUserId));
    }

    @Transactional
    public void deleteFriend(String currentUserId, String targetUserId) {
        String u1 = currentUserId.compareTo(targetUserId) < 0 ? currentUserId : targetUserId;
        String u2 = currentUserId.compareTo(targetUserId) < 0 ? targetUserId : currentUserId;

        FriendEntity relation = friendJpaRepository.findByUserId1AndUserId2(u1, u2)
                .orElseThrow(() -> NotFoundException.friend());

        friendJpaRepository.delete(relation);

        // [정책] 친구 삭제 시 기존 모임의 MeetingParticipant 데이터는 유지됩니다.
        // 이미 확정(CONFIRMED)되거나 진행 중인 모임의 참여 이력을 보존하는 것이 의도된 동작입니다.
        // 모임에서도 해당 사용자를 제거하려면 모임 호스트가 직접 모임을 수정해야 합니다.
    }
}
