package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.SessionFeedback;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.SessionFeedbackRepository;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

  @Mock private SessionFeedbackRepository sessionFeedbackRepository;
  @Mock private ReputationService reputationService;
  @Mock private FriendshipService friendshipService;
  @Mock private ChatSessionRepository chatSessionRepository;

  private FeedbackService feedbackService;

  @BeforeEach
  void setUp() {
    feedbackService =
        new FeedbackService(
            sessionFeedbackRepository, reputationService, friendshipService, chatSessionRepository);
  }

  private ChatSession session(ChatSession.SessionStatus status) {
    return ChatSession.builder()
        .id("session-1")
        .user1Id("user-1")
        .user2Id("user-2")
        .status(status)
        .build();
  }

  private void lockReturns(ChatSession s) {
    when(chatSessionRepository.findByIdForUpdate("session-1")).thenReturn(Optional.of(s));
  }

  private void insertReturns(int rows) {
    when(sessionFeedbackRepository.insertIfAbsent(
            any(), eq("session-1"), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(rows);
  }

  private SessionFeedback reverse(boolean liked) {
    return SessionFeedback.builder()
        .sessionId("session-1")
        .fromUserId("user-2")
        .toUserId("user-1")
        .liked(liked)
        .build();
  }

  @Test
  @DisplayName("should reject feedback on a session that is still active")
  void shouldRejectActiveSession() {
    ChatSession active = session(ChatSession.SessionStatus.ACTIVE);
    lockReturns(active);

    assertThrows(
        IllegalStateException.class, () -> feedbackService.submitFeedback(active, "user-1", true));
    verifyNoInteractions(reputationService, friendshipService, sessionFeedbackRepository);
  }

  @Test
  @DisplayName("should return false on duplicate feedback without touching reputation")
  void shouldReturnFalseOnDuplicate() {
    ChatSession ended = session(ChatSession.SessionStatus.ENDED);
    lockReturns(ended);
    insertReturns(0);

    assertFalse(feedbackService.submitFeedback(ended, "user-1", true));
    verifyNoInteractions(reputationService, friendshipService);
  }

  @Test
  @DisplayName("should store feedback for the other user and apply reputation")
  void shouldStoreFeedbackAndApplyReputation() {
    ChatSession ended = session(ChatSession.SessionStatus.ENDED);
    lockReturns(ended);
    insertReturns(1);

    assertTrue(feedbackService.submitFeedback(ended, "user-2", false));

    verify(sessionFeedbackRepository)
        .insertIfAbsent(any(), eq("session-1"), eq("user-2"), eq("user-1"), eq(false), any());
    verify(reputationService).applyFeedback("user-1", false);
    verifyNoInteractions(friendshipService);
  }

  @Test
  @DisplayName("should create friendship when the reverse like is visible after our write")
  void shouldCreateFriendshipOnMutualLike() {
    ChatSession ended = session(ChatSession.SessionStatus.ENDED);
    lockReturns(ended);
    insertReturns(1);
    when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
        .thenReturn(Optional.of(reverse(true)));

    feedbackService.submitFeedback(ended, "user-1", true);

    verify(friendshipService).createFriendshipIfAbsent("user-1", "user-2");
  }

  @Test
  @DisplayName("should not create friendship when the reverse feedback is a dislike")
  void shouldNotCreateFriendshipWhenReverseDislike() {
    ChatSession ended = session(ChatSession.SessionStatus.ENDED);
    lockReturns(ended);
    insertReturns(1);
    when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
        .thenReturn(Optional.of(reverse(false)));

    feedbackService.submitFeedback(ended, "user-1", true);

    verifyNoInteractions(friendshipService);
  }

  @Test
  @DisplayName("should not create friendship when there is no reverse feedback")
  void shouldNotCreateFriendshipWithoutReverse() {
    ChatSession ended = session(ChatSession.SessionStatus.ENDED);
    lockReturns(ended);
    insertReturns(1);
    when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
        .thenReturn(Optional.empty());

    feedbackService.submitFeedback(ended, "user-1", true);

    verifyNoInteractions(friendshipService);
  }
}
