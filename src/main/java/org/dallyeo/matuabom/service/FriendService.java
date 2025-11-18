package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.Friend;
import org.dallyeo.matuabom.domain.InviteCode;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.repository.FriendRepository;
import org.dallyeo.matuabom.repository.InviteCodeRepository;
import org.dallyeo.matuabom.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendRepository friendRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;

    @Transactional
    public User addFriend(String currentUserId, String code) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자에 초대코드가 없습니다."));

        String codeOwnerUserId = inviteCode.getOwnerUserId();
        if (codeOwnerUserId.equals(currentUserId)) {
            throw new IllegalStateException("자기 자신의 초대코드는 사용할 수 없습니다.");
        }

        // 두 userId를 정렬해서 항상 같은 순서로 저장
        String userId1 = codeOwnerUserId.compareTo(currentUserId) < 0 ? codeOwnerUserId : currentUserId;
        String userId2 = codeOwnerUserId.compareTo(currentUserId) < 0 ? currentUserId : codeOwnerUserId;

        if (friendRepository.existsByUserId1AndUserId2(userId1, userId2)) {
            throw new IllegalStateException("이미 친구입니다.");
        }

        Friend friend = Friend.create(userId1, userId2);
        friendRepository.save(friend);

        return userRepository.findById(codeOwnerUserId)
                .orElseThrow(() -> new IllegalStateException("친구 유저 정보가 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public List<User> getFriendsList(String userId) {
        List<Friend> relations = friendRepository.findByUserId1OrUserId2(userId, userId); //userId1 = userId 인 경우와 userId2 = userId 인 경우를 찾아 옴. (중복 파라미터 혼동x)
        List<String> friendIds = relations.stream()
                .map(friend -> friend.getUserId1().equals(userId) ? friend.getUserId2() : friend.getUserId1())
                .distinct()
                .toList();
        if (friendIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(friendIds);
    }
}
