package shi.raibu.shi.service;

import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.SessionFeedback;
import shi.raibu.shi.repository.SessionFeedbackRepository;

@Service
@AllArgsConstructor
public class FeedbackService {

  private final SessionFeedbackRepository sessionFeedbackRepository;
  private final ReputationService reputationService;
  private final FriendshipService friendshipService;

  public boolean submitFeedback(ChatSession session, String fromUserId, boolean liked) {
    // Idempotent: if feedback already exists for this (session, fromUserId), do nothing
    if (sessionFeedbackRepository
        .findBySessionIdAndFromUserId(session.getId(), fromUserId)
        .isPresent()) {
      return false;
    }

    String toUserId =
        fromUserId.equals(session.getUser1Id()) ? session.getUser2Id() : session.getUser1Id();

    SessionFeedback feedback =
        SessionFeedback.builder()
            .sessionId(session.getId())
            .fromUserId(fromUserId)
            .toUserId(toUserId)
            .liked(liked)
            .createdAt(Instant.now())
            .build();

    sessionFeedbackRepository.save(feedback);

    reputationService.applyFeedback(toUserId, liked);

    // If the user liked the session, check for mutual like and potentially create a friendship.
    if (liked) {
      Optional<SessionFeedback> reverseFeedbackOpt =
          sessionFeedbackRepository.findBySessionIdAndFromUserId(session.getId(), toUserId);

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
