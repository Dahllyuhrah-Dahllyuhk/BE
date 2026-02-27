package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.FriendEntity;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.repository.jpa.FriendJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.InviteCodeJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.dallyeo.matuabom.user.domain.InviteCodeEntity;
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
                    .orElseThrow(() -> new IllegalArgumentException("해당 초대코드가 없습니다."));
            codeOwnerUserId = inviteCode.getOwnerUserId();
            tokenStore.cacheInviteCode(code, codeOwnerUserId);
        }

        if (codeOwnerUserId.equals(currentUserId)) {
            throw new IllegalStateException("자신의 초대코드는 사용할 수 없습니다.");
        }

        // userId 정렬 (중복 방지)
        String u1 = codeOwnerUserId.compareTo(currentUserId) < 0 ? codeOwnerUserId : currentUserId;
        String u2 = codeOwnerUserId.compareTo(currentUserId) < 0 ? currentUserId : codeOwnerUserId;

        if (friendJpaRepository.existsByUserId1AndUserId2(u1, u2)) {
            throw new IllegalStateException("이미 친구입니다.");
        }

        friendJpaRepository.save(FriendEntity.create(codeOwnerUserId, currentUserId));

        final String ownerUserId = codeOwnerUserId;
        return userJpaRepository.findByMongoId(ownerUserId)
                .orElseThrow(() -> new IllegalStateException("친구 정보가 없습니다."));
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
    public void deleteFriend(String currentUserId, String targetUserId) {
        String u1 = currentUserId.compareTo(targetUserId) < 0 ? currentUserId : targetUserId;
        String u2 = currentUserId.compareTo(targetUserId) < 0 ? targetUserId : currentUserId;

        FriendEntity relation = friendJpaRepository.findByUserId1AndUserId2(u1, u2)
                .orElseThrow(() -> new IllegalArgumentException("친구가 아닙니다."));

        friendJpaRepository.delete(relation);
    }
}
