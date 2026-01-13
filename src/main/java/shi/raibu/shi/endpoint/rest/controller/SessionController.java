package shi.raibu.shi.endpoint.rest.controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.ChatMessage;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.repository.ChatMessageRepository;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.service.FeedbackService;

@RestController
@RequestMapping("/sessions")
@AllArgsConstructor
public class SessionController {

  private final ChatSessionRepository chatSessionRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final FeedbackService feedbackService;

  @GetMapping("/me")
  public ResponseEntity<List<ChatSession>> getMySessions(Principal principal) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();
    List<ChatSession> sessions = chatSessionRepository.findByUser1IdOrUser2Id(userId, userId);
    return ResponseEntity.ok(sessions);
  }

  @GetMapping("/{sessionId}/messages")
  public ResponseEntity<List<ChatMessage>> getSessionMessages(
      Principal principal, @PathVariable String sessionId) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();

    Optional<ChatSession> sessionOpt = chatSessionRepository.findById(sessionId);
    if (sessionOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    ChatSession session = sessionOpt.get();
    if (!userId.equals(session.getUser1Id()) && !userId.equals(session.getUser2Id())) {
      return ResponseEntity.status(403).build();
    }

    List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderBySentAtAsc(sessionId);
    return ResponseEntity.ok(messages);
  }

  @PostMapping("/{sessionId}/feedback")
  public ResponseEntity<Void> submitFeedback(
      Principal principal, @PathVariable String sessionId, @RequestBody FeedbackRequest request) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();

    Optional<ChatSession> sessionOpt = chatSessionRepository.findById(sessionId);
    if (sessionOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    ChatSession session = sessionOpt.get();
    if (!userId.equals(session.getUser1Id()) && !userId.equals(session.getUser2Id())) {
      return ResponseEntity.status(403).build();
    }

    feedbackService.submitFeedback(session, userId, request.liked());

    return ResponseEntity.ok().build();
  }

  public record FeedbackRequest(boolean liked) {}
}
