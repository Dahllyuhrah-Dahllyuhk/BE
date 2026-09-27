package org.dallyeo.matuabom.user.service;

import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.Friend;
import org.dallyeo.matuabom.user.domain.User;
import org.dallyeo.matuabom.user.repository.FriendRepository;
import org.dallyeo.matuabom.user.repository.InviteCodeRepository;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock FriendRepository friendRepository;
    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock UserRepository userRepository;
    @Mock TokenStore tokenStore;

    @InjectMocks FriendService friendService;

    @Test
    @DisplayName("자신의 초대코드로는 친구 추가 불가")
    void cannotAddSelfAsFriend() {
        given(tokenStore.getCachedInviteOwner("MYCODE")).willReturn("user123");

        assertThatThrownBy(() -> friendService.addFriend("user123", "MYCODE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("자신의 초대코드");
    }

    @Test
    @DisplayName("이미 친구인 경우 중복 추가 불가")
    void cannotAddDuplicateFriend() {
        given(tokenStore.getCachedInviteOwner("CODE1")).willReturn("user456");
        given(friendRepository.existsByUserId1AndUserId2(any(), any())).willReturn(true);

        assertThatThrownBy(() -> friendService.addFriend("user123", "CODE1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 친구");
    }

    @Test
    @DisplayName("유효하지 않은 초대코드 사용 시 예외")
    void invalidInviteCodeThrowsException() {
        given(tokenStore.getCachedInviteOwner("INVALID")).willReturn(null);
        given(inviteCodeRepository.findByCode("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.addFriend("user123", "INVALID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("초대코드");
    }

    @Test
    @DisplayName("정상적인 친구 추가")
    void addFriendSuccessfully() {
        User targetUser = User.builder()
            .id("user456").nickname("친구유저").build();

        given(tokenStore.getCachedInviteOwner("CODE2")).willReturn("user456");
        given(friendRepository.existsByUserId1AndUserId2(any(), any())).willReturn(false);
        given(friendRepository.save(any())).willReturn(Friend.create("user123", "user456"));
        given(userRepository.findById("user456")).willReturn(Optional.of(targetUser));

        User result = friendService.addFriend("user123", "CODE2");

        assertThat(result.getNickname()).isEqualTo("친구유저");
        then(friendRepository).should().save(any());
    }
}
