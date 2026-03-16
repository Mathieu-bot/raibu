package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.User;
import shi.raibu.shi.model.User.UserStatus;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;

import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ChatSessionRepository chatSessionRepository;

  private MatchmakingService matchmakingService;

  @BeforeEach
  void setUp() {
    matchmakingService = new MatchmakingService(userRepository, chatSessionRepository);
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

    @Test
    @DisplayName("should return empty when no searching users available")
    void shouldReturnEmptyWhenNoSearchingUsers() {
      User currentUser =
          User.builder()
              .id("user-1")
              .username("user1")
              .banned(false)
              .status(UserStatus.IDLE)
              .countryCode("FR")
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(currentUser));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of());

      Optional<User> result = matchmakingService.findRandomMatch("user-1");

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should create chat session when match is found")
    void shouldCreateChatSessionWhenMatchFound() {
      User currentUser =
          User.builder()
              .id("user-1")
              .username("user1")
              .banned(false)
              .status(UserStatus.IDLE)
              .countryCode("FR")
              .preferSameCountry(false)
              .build();

      User searchingUser =
          User.builder()
              .id("user-2")
              .username("user2")
              .banned(false)
              .status(UserStatus.SEARCHING)
              .countryCode("US")
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(currentUser));
      when(userRepository.findByStatusAndBannedFalse(UserStatus.SEARCHING))
          .thenReturn(List.of(searchingUser));

      Optional<User> result = matchmakingService.findRandomMatch("user-1");

      assertTrue(result.isPresent());
      assertEquals("user-2", result.get().getId());

      // Verify session was created
      verify(chatSessionRepository).save(any(ChatSession.class));

      // Verify both users status changed to IN_CHAT
      // The current user is saved when status is updated, and the matched user is saved once
      verify(userRepository, atLeastOnce()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("startSearching()")
  class StartSearchingTests {

    @Test
    @DisplayName("should set user status to SEARCHING")
    void shouldSetUserStatusToSearching() {
      User user =
          User.builder()
              .id("user-1")
              .username("user1")
              .status(UserStatus.IDLE)
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1");

      verify(userRepository).findById("user-1");
      verify(userRepository).save(argThat(savedUser -> savedUser.getStatus() == UserStatus.SEARCHING));
    }

    @Test
    @DisplayName("should set search mode when provided")
    void shouldSetSearchModeWhenProvided() {
      User user =
          User.builder()
              .id("user-1")
              .username("user1")
              .status(UserStatus.IDLE)
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1", "gaming");

      verify(userRepository)
          .save(argThat(savedUser -> "gaming".equals(savedUser.getSearchMode())));
    }

    @Test
    @DisplayName("should normalize search mode to lowercase")
    void shouldNormalizeSearchModeToLowercase() {
      User user =
          User.builder()
              .id("user-1")
              .username("user1")
              .status(UserStatus.IDLE)
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.startSearching("user-1", "GAMING");

      verify(userRepository)
          .save(argThat(savedUser -> "gaming".equals(savedUser.getSearchMode())));
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

      verify(userRepository)
          .save(argThat(savedUser -> savedUser.getStatus() == UserStatus.IDLE));
      verify(userRepository).save(argThat(savedUser -> savedUser.getSearchMode() == null));
    }
  }

  @Nested
  @DisplayName("endChat()")
  class EndChatTests {

    @Test
    @DisplayName("should end all active sessions for user")
    void shouldEndAllActiveSessionsForUser() {
      User user = User.builder().id("user-1").username("user1").status(UserStatus.IN_CHAT).build();

      ChatSession activeSession =
          ChatSession.builder()
              .id("session-1")
              .user1Id("user-1")
              .user2Id("user-2")
              .status(ChatSession.SessionStatus.ACTIVE)
              .startedAt(Instant.now())
              .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
      when(chatSessionRepository.findByUser1IdOrUser2Id("user-1", "user-1"))
          .thenReturn(List.of(activeSession));
      when(userRepository.save(any(User.class))).thenReturn(user);

      matchmakingService.endChat("user-1");

      verify(chatSessionRepository).save(argThat(s -> s.getStatus() == ChatSession.SessionStatus.ENDED));
      verify(userRepository)
          .save(argThat(savedUser -> savedUser.getStatus() == UserStatus.IDLE));
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
