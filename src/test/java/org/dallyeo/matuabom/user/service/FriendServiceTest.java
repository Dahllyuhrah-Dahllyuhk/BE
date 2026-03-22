package org.dallyeo.matuabom.user.service;

import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.user.domain.FriendEntity;
import org.dallyeo.matuabom.user.domain.InviteCodeEntity;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.repository.jpa.FriendJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.InviteCodeJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
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

    @Mock FriendJpaRepository friendJpaRepository;
    @Mock InviteCodeJpaRepository inviteCodeJpaRepository;
    @Mock UserJpaRepository userJpaRepository;
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
        given(friendJpaRepository.existsByUserId1AndUserId2(any(), any())).willReturn(true);

        assertThatThrownBy(() -> friendService.addFriend("user123", "CODE1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 친구");
    }

    @Test
    @DisplayName("유효하지 않은 초대코드 사용 시 예외")
    void invalidInviteCodeThrowsException() {
        given(tokenStore.getCachedInviteOwner("INVALID")).willReturn(null);
        given(inviteCodeJpaRepository.findByCode("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.addFriend("user123", "INVALID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("초대코드");
    }

    @Test
    @DisplayName("정상적인 친구 추가")
    void addFriendSuccessfully() {
        UserEntity targetUser = UserEntity.builder()
            .id(2L).mongoId("user456").nickname("친구유저").build();

        given(tokenStore.getCachedInviteOwner("CODE2")).willReturn("user456");
        given(friendJpaRepository.existsByUserId1AndUserId2(any(), any())).willReturn(false);
        given(friendJpaRepository.save(any())).willReturn(new FriendEntity());
        given(userJpaRepository.findByMongoId("user456")).willReturn(Optional.of(targetUser));

        UserEntity result = friendService.addFriend("user123", "CODE2");

        assertThat(result.getNickname()).isEqualTo("친구유저");
        then(friendJpaRepository).should().save(any());
    }
}
