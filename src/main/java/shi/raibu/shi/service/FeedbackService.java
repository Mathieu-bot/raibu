package shi.raibu.shi.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.SessionFeedback;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.SessionFeedbackRepository;

@Service
@AllArgsConstructor
public class FeedbackService {

  private final SessionFeedbackRepository sessionFeedbackRepository;
  private final ReputationService reputationService;
  private final FriendshipService friendshipService;
  private final ChatSessionRepository chatSessionRepository;

  /**
   * Records feedback for an ENDED session. Returns false when this user already sent feedback.
   *
   * <p>The session row is locked first so two simultaneous feedbacks on the same session are
   * serialized: the second one then sees the first one's committed row, so a simultaneous mutual
   * like still creates the friendship.
   */
  @Transactional
  public boolean submitFeedback(ChatSession session, String fromUserId, boolean liked) {
    ChatSession locked =
        chatSessionRepository
            .findByIdForUpdate(session.getId())
            .orElseThrow(() -> new IllegalStateException("Session not found"));
    if (locked.getStatus() != ChatSession.SessionStatus.ENDED) {
      throw new IllegalStateException("Feedback is only accepted once the session has ended");
    }

    String toUserId =
        fromUserId.equals(locked.getUser1Id()) ? locked.getUser2Id() : locked.getUser1Id();

    // Idempotent insert: a duplicate (session, fromUser) is ignored by the unique constraint.
    int inserted =
        sessionFeedbackRepository.insertIfAbsent(
            UUID.randomUUID().toString(),
            locked.getId(),
            fromUserId,
            toUserId,
            liked,
            Instant.now());
    if (inserted == 0) {
      return false;
    }

    reputationService.applyFeedback(toUserId, liked);

    // Re-check the reverse feedback after our own write to detect a mutual like.
    if (liked) {
      Optional<SessionFeedback> reverseFeedbackOpt =
          sessionFeedbackRepository.findBySessionIdAndFromUserId(locked.getId(), toUserId);

      if (reverseFeedbackOpt.isPresent()) {
        SessionFeedback reverse = reverseFeedbackOpt.get();
        if (reverse.isLiked() && reverse.getToUserId().equals(fromUserId)) {
          friendshipService.createFriendshipIfAbsent(fromUserId, toUserId);
        }
      }
    }

    return true;
  }
}
