package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.SessionFeedback;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.SessionFeedbackRepository;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

  @Mock private SessionFeedbackRepository sessionFeedbackRepository;
  @Mock private ReputationService reputationService;
  @Mock private FriendshipService friendshipService;

  private FeedbackService feedbackService;

  @BeforeEach
  void setUp() {
    feedbackService = new FeedbackService(sessionFeedbackRepository, reputationService, friendshipService);
  }

  @Nested
  @DisplayName("submitFeedback()")
  class SubmitFeedbackTests {

    @Test
    @DisplayName("should return false when feedback already exists")
    void shouldReturnFalseWhenFeedbackExists() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      SessionFeedback existingFeedback = SessionFeedback.builder()
          .id("feedback-1")
          .sessionId("session-1")
          .fromUserId("user-1")
          .toUserId("user-2")
          .liked(true)
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.of(existingFeedback));

      boolean result = feedbackService.submitFeedback(session, "user-1", true);

      assertFalse(result);
      verify(sessionFeedbackRepository).findBySessionIdAndFromUserId("session-1", "user-1");
      verify(sessionFeedbackRepository, never()).save(any(SessionFeedback.class));
      verifyNoInteractions(reputationService, friendshipService);
    }

    @Test
    @DisplayName("should create feedback and apply reputation for like")
    void shouldCreateFeedbackAndApplyReputationForLike() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      boolean result = feedbackService.submitFeedback(session, "user-1", true);

      assertTrue(result);
      verify(sessionFeedbackRepository).save(argThat(feedback ->
          feedback.getSessionId().equals("session-1") &&
          feedback.getFromUserId().equals("user-1") &&
          feedback.getToUserId().equals("user-2") &&
          feedback.isLiked()
      ));
      verify(reputationService).applyFeedback("user-2", true);
    }

    @Test
    @DisplayName("should create feedback and apply reputation for dislike")
    void shouldCreateFeedbackAndApplyReputationForDislike() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      boolean result = feedbackService.submitFeedback(session, "user-1", false);

      assertTrue(result);
      verify(sessionFeedbackRepository).save(argThat(feedback ->
          feedback.getSessionId().equals("session-1") &&
          feedback.getFromUserId().equals("user-1") &&
          feedback.getToUserId().equals("user-2") &&
          !feedback.isLiked()
      ));
      verify(reputationService).applyFeedback("user-2", false);
    }

    @Test
    @DisplayName("should determine correct toUserId when fromUserId is user1")
    void shouldDetermineToUserIdWhenFromUserIsUser1() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-1", true);

      verify(sessionFeedbackRepository).save(argThat(feedback ->
          feedback.getFromUserId().equals("user-1") &&
          feedback.getToUserId().equals("user-2")
      ));
    }

    @Test
    @DisplayName("should determine correct toUserId when fromUserId is user2")
    void shouldDetermineToUserIdWhenFromUserIsUser2() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-2", true);

      verify(sessionFeedbackRepository).save(argThat(feedback ->
          feedback.getFromUserId().equals("user-2") &&
          feedback.getToUserId().equals("user-1")
      ));
    }

    @Test
    @DisplayName("should not create friendship when feedback is dislike")
    void shouldNotCreateFriendshipWhenDislike() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-1", false);

      verifyNoInteractions(friendshipService);
    }

    @Test
    @DisplayName("should not create friendship when reverse feedback does not exist")
    void shouldNotCreateFriendshipWhenReverseFeedbackDoesNotExist() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-1", true);

      verifyNoInteractions(friendshipService);
    }

    @Test
    @DisplayName("should create friendship when both users liked each other")
    void shouldCreateFriendshipWhenMutualLike() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      SessionFeedback reverseFeedback = SessionFeedback.builder()
          .id("feedback-2")
          .sessionId("session-1")
          .fromUserId("user-2")
          .toUserId("user-1")
          .liked(true)
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
          .thenReturn(Optional.of(reverseFeedback));
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-1", true);

      verify(friendshipService).createFriendshipIfAbsent("user-1", "user-2");
    }

    @Test
    @DisplayName("should not create friendship when reverse feedback is dislike")
    void shouldNotCreateFriendshipWhenReverseFeedbackIsDislike() {
      ChatSession session = ChatSession.builder()
          .id("session-1")
          .user1Id("user-1")
          .user2Id("user-2")
          .build();

      SessionFeedback reverseFeedback = SessionFeedback.builder()
          .id("feedback-2")
          .sessionId("session-1")
          .fromUserId("user-2")
          .toUserId("user-1")
          .liked(false)
          .build();

      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-1"))
          .thenReturn(Optional.empty());
      when(sessionFeedbackRepository.findBySessionIdAndFromUserId("session-1", "user-2"))
          .thenReturn(Optional.of(reverseFeedback));
      when(sessionFeedbackRepository.save(any(SessionFeedback.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      feedbackService.submitFeedback(session, "user-1", true);

      verifyNoInteractions(friendshipService);
    }
  }
}
