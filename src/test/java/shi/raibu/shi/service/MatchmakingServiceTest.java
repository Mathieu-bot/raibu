package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.User;
import shi.raibu.shi.model.User.UserStatus;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.websocket.model.SignalMessage;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ChatSessionRepository chatSessionRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;

  private MatchmakingService matchmakingService;

  @BeforeEach
  void setUp() {
    matchmakingService =
        new MatchmakingService(userRepository, chatSessionRepository, messagingTemplate);
  }

  @Nested
  @DisplayName("findRandomMatch()")
  class FindRandomMatchTests {

    @Test
    @DisplayName("should return empty when user does not exist")
    void shouldReturnEmptyWhenUserDoesNotExist() {
      when(userRepository.findById("non-existing-user")).thenReturn(Optional.empty());

      Optional<User> result = matchmakingService.findRandomMatch("non-existing-user");

      assertTrue(result.isEmpty());
      verify(userRepository).findById("non-existing-user");
      verifyNoMoreInteractions(userRepository, chatSessionRepository);
    }

    @Test
    @DisplayName("should return empty when user is banned")
    void shouldReturnEmptyWhenUserIsBanned() {
      User bannedUser =
          User.builder()
              .id("user-1")
              .username("banned")
              .banned(true)
              .status(UserStatus.IDLE)
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(bannedUser));

      Optional<User> result = matchmakingService.findRandomMatch("user-1");

      assertTrue(result.isEmpty());
      verify(userRepository).findById("user-1");
      verifyNoInteractions(chatSessionRepository);
    }

    private User searching(String id) {
      return User.builder()
          .id(id)
          .username(id)
          .banned(false)
          .status(UserStatus.SEARCHING)
          .preferSameCountry(false)
          .build();
    }

    private void stubClaim(List<String> claimable) {
      when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of());
      when(userRepository.findIdsByIdInAndStatusAndBannedFalse(any(), eq(UserStatus.SEARCHING)))
          .thenReturn(claimable);
    }

    @Test
    @DisplayName("should return empty when the caller is not searching")
    void shouldReturnEmptyWhenCallerNotSearching() {
      User caller = searching("user-1");
      caller.setStatus(UserStatus.IN_CHAT);
      when(userRepository.findById("user-1")).thenReturn(Optional.of(caller));

      assertTrue(matchmakingService.findRandomMatch("user-1").isEmpty());
      verifyNoInteractions(chatSessionRepository);
    }

    @Test
    @DisplayName("should return empty when no searching users available")
    void shouldReturnEmptyWhenNoSearchingUsers() {
      when(userRepository.findById("user-1")).thenReturn(Optional.of(searching("user-1")));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(searching("user-1")));

      assertTrue(matchmakingService.findRandomMatch("user-1").isEmpty());
      verify(userRepository, never()).findAllByIdInOrderById(any());
    }

    @Test
    @DisplayName("should claim both users, close stale sessions and create one session")
    void shouldClaimBothUsersAndCreateSession() {
      User caller = searching("user-1");
      User other = searching("user-2");
      ChatSession stale =
          ChatSession.builder()
              .id("old")
              .user1Id("user-2")
              .user2Id("user-9")
              .status(ChatSession.SessionStatus.ACTIVE)
              .build();
      when(userRepository.findById("user-1")).thenReturn(Optional.of(caller));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(caller, other));
      stubClaim(List.of("user-1", "user-2"));
      when(chatSessionRepository.findActiveByUserIds(any())).thenReturn(List.of(stale));

      Optional<User> result = matchmakingService.findRandomMatch("user-1");

      assertEquals("user-2", result.orElseThrow().getId());
      assertEquals(ChatSession.SessionStatus.ENDED, stale.getStatus());
      assertNotNull(stale.getEndedAt());
      InOrder order = inOrder(userRepository, chatSessionRepository);
      order.verify(userRepository).findAllByIdInOrderById(List.of("user-1", "user-2"));
      order.verify(chatSessionRepository).findActiveByUserIds(any());
      order.verify(chatSessionRepository, times(2)).save(any(ChatSession.class));
      order.verify(userRepository).updateStatusByIdIn(any(), eq(UserStatus.IN_CHAT), any());
    }

    @Test
    @DisplayName("should lock rows in id order whatever the caller is")
    void shouldLockInIdOrder() {
      User caller = searching("zzz");
      User other = searching("aaa");
      when(userRepository.findById("zzz")).thenReturn(Optional.of(caller));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(caller, other));
      stubClaim(List.of("zzz", "aaa"));
      when(chatSessionRepository.findActiveByUserIds(any())).thenReturn(List.of());

      matchmakingService.findRandomMatch("zzz");

      // The repository query sorts by id; the service must pass both ids in one call.
      verify(userRepository).findAllByIdInOrderById(argThat(ids -> ids.size() == 2));
    }

    @Test
    @DisplayName("should not create a session when the caller was claimed by someone else")
    void shouldAbortWhenCallerNoLongerSearching() {
      User caller = searching("user-1");
      when(userRepository.findById("user-1")).thenReturn(Optional.of(caller));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(caller, searching("user-2")));
      stubClaim(List.of("user-2"));

      assertTrue(matchmakingService.findRandomMatch("user-1").isEmpty());
      verify(chatSessionRepository, never()).save(any());
      verify(userRepository, never()).updateStatusByIdIn(any(), any(), any());
    }

    @Test
    @DisplayName("should try another candidate when the first one was taken meanwhile")
    void shouldRetryWithAnotherCandidate() {
      User caller = searching("user-1");
      when(userRepository.findById("user-1")).thenReturn(Optional.of(caller));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(caller, searching("user-2"), searching("user-3")));
      when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of());
      // Whoever is picked first is gone; the second pick is claimable.
      when(userRepository.findIdsByIdInAndStatusAndBannedFalse(any(), eq(UserStatus.SEARCHING)))
          .thenReturn(List.of("user-1"))
          .thenAnswer(
              inv ->
                  new java.util.ArrayList<>(((java.util.Collection<String>) inv.getArgument(0))));
      when(chatSessionRepository.findActiveByUserIds(any())).thenReturn(List.of());

      Optional<User> result = matchmakingService.findRandomMatch("user-1");

      assertTrue(result.isPresent());
      verify(chatSessionRepository).save(any(ChatSession.class));
      verify(userRepository, times(2)).findAllByIdInOrderById(any());
    }

    @Test
    @DisplayName("should never match a very low reputation user, even through the fallback pick")
    void shouldExcludeLowReputationFromFallback() {
      User caller = searching("user-1");
      User toxic = searching("user-2");
      toxic.setReputationScore(-50);
      when(userRepository.findById("user-1")).thenReturn(Optional.of(caller));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(caller, toxic));

      assertTrue(matchmakingService.findRandomMatch("user-1").isEmpty());
      verify(userRepository, never()).findAllByIdInOrderById(any());
    }
  }

  @Nested
  @DisplayName("startSearching()")
  class StartSearchingTests {

    @Test
    @DisplayName("should set user status to SEARCHING")
    void shouldSetUserStatusToSearching() {
      User user = User.builder().id("user-1").username("user1").status(UserStatus.IDLE).build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1");

      verify(userRepository).findById("user-1");
      verify(userRepository)
          .save(argThat(savedUser -> savedUser.getStatus() == UserStatus.SEARCHING));
    }

    @Test
    @DisplayName("should set search mode when provided")
    void shouldSetSearchModeWhenProvided() {
      User user = User.builder().id("user-1").username("user1").status(UserStatus.IDLE).build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1", "gaming");

      verify(userRepository).save(argThat(savedUser -> "gaming".equals(savedUser.getSearchMode())));
    }

    @Test
    @DisplayName("should normalize search mode to lowercase")
    void shouldNormalizeSearchModeToLowercase() {
      User user = User.builder().id("user-1").username("user1").status(UserStatus.IDLE).build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1", "GAMING");

      verify(userRepository).save(argThat(savedUser -> "gaming".equals(savedUser.getSearchMode())));
    }
  }

  @Nested
  @DisplayName("stopSearching()")
  class StopSearchingTests {

    @Test
    @DisplayName("should set user status back to IDLE")
    void shouldSetUserStatusBackToIdle() {
      User user =
          User.builder()
              .id("user-1")
              .username("user1")
              .status(UserStatus.SEARCHING)
              .searchMode("gaming")
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.stopSearching("user-1");

      verify(userRepository).save(argThat(savedUser -> savedUser.getStatus() == UserStatus.IDLE));
      verify(userRepository).save(argThat(savedUser -> savedUser.getSearchMode() == null));
    }
  }

  @Nested
  @DisplayName("endChat()")
  class EndChatTests {

    private User user(String id, UserStatus status) {
      return User.builder().id(id).username(id).status(status).build();
    }

    private ChatSession active() {
      return ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .status(ChatSession.SessionStatus.ACTIVE)
          .startedAt(Instant.now())
          .build();
    }

    @Test
    @DisplayName("should end the session, release both users and notify the peer")
    void shouldEndSessionReleaseBothAndNotifyPeer() {
      ChatSession session = active();
      when(chatSessionRepository.findActiveByUserIds(List.of("user-1")))
          .thenReturn(List.of(session));
      when(userRepository.findAllByIdInOrderById(any()))
          .thenReturn(
              List.of(user("user-1", UserStatus.IN_CHAT), user("user-2", UserStatus.IN_CHAT)));

      matchmakingService.endChat("user-1");

      assertEquals(ChatSession.SessionStatus.ENDED, session.getStatus());
      assertNotNull(session.getEndedAt());
      verify(userRepository)
          .updateStatusByIdIn(
              argThat(ids -> ids.containsAll(List.of("user-1", "user-2")) && ids.size() == 2),
              eq(UserStatus.IDLE),
              any());
      verify(messagingTemplate)
          .convertAndSendToUser(
              eq("user-2"),
              eq("/queue/match"),
              argThat(
                  (SignalMessage m) ->
                      m.getType() == SignalMessage.SignalType.PEER_DISCONNECTED
                          && "user-1".equals(m.getData())));
    }

    @Test
    @DisplayName("should not touch a peer that is already searching again")
    void shouldNotReleasePeerThatMovedOn() {
      when(chatSessionRepository.findActiveByUserIds(List.of("user-1")))
          .thenReturn(List.of(active()));
      when(userRepository.findAllByIdInOrderById(any()))
          .thenReturn(
              List.of(user("user-1", UserStatus.IN_CHAT), user("user-2", UserStatus.SEARCHING)));

      matchmakingService.endChat("user-1");

      verify(userRepository)
          .updateStatusByIdIn(
              argThat(ids -> List.copyOf(ids).equals(List.of("user-1"))),
              eq(UserStatus.IDLE),
              any());
    }

    @Test
    @DisplayName("should keep an OFFLINE caller offline")
    void shouldKeepOfflineCallerOffline() {
      when(chatSessionRepository.findActiveByUserIds(List.of("user-1"))).thenReturn(List.of());
      when(userRepository.findAllByIdInOrderById(any()))
          .thenReturn(List.of(user("user-1", UserStatus.OFFLINE)));

      matchmakingService.endChat("user-1");

      verify(userRepository, never()).updateStatusByIdIn(any(), any(), any());
      verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("should release the caller when there is no active session")
    void shouldReleaseCallerWithoutSession() {
      when(chatSessionRepository.findActiveByUserIds(List.of("user-1"))).thenReturn(List.of());
      when(userRepository.findAllByIdInOrderById(any()))
          .thenReturn(List.of(user("user-1", UserStatus.SEARCHING)));

      matchmakingService.endChat("user-1");

      verify(userRepository).updateStatusByIdIn(any(), eq(UserStatus.IDLE), any());
      verifyNoInteractions(messagingTemplate);
    }
  }

  @Nested
  @DisplayName("isUserBanned()")
  class IsUserBannedTests {

    @Test
    @DisplayName("should return true when user is banned")
    void shouldReturnTrueWhenUserIsBanned() {
      User bannedUser = User.builder().id("user-1").banned(true).build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(bannedUser));

      assertTrue(matchmakingService.isUserBanned("user-1"));
    }

    @Test
    @DisplayName("should return false when user is not banned")
    void shouldReturnFalseWhenUserIsNotBanned() {
      User activeUser = User.builder().id("user-1").banned(false).build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));

      assertFalse(matchmakingService.isUserBanned("user-1"));
    }

    @Test
    @DisplayName("should return false when user does not exist")
    void shouldReturnFalseWhenUserDoesNotExist() {
      when(userRepository.findById("non-existing")).thenReturn(Optional.empty());

      assertFalse(matchmakingService.isUserBanned("non-existing"));
    }
  }
}
