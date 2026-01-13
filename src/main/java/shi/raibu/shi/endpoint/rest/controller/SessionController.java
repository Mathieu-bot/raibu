package shi.raibu.shi.endpoint.rest.controller;

import java.security.Principal;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.repository.ChatSessionRepository;

@RestController
@RequestMapping("/sessions")
@AllArgsConstructor
public class SessionController {

  private final ChatSessionRepository chatSessionRepository;

  @GetMapping("/me")
  public ResponseEntity<List<ChatSession>> getMySessions(Principal principal) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();
    List<ChatSession> sessions = chatSessionRepository.findByUser1IdOrUser2Id(userId, userId);
    return ResponseEntity.ok(sessions);
  }
}
