package shi.raibu.shi.endpoint.rest.controller;

import java.time.Instant;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {
  private final UserRepository userRepository;

  @PostMapping("/register")
  public ResponseEntity<User> registerAnonymousUser() {
    User user =
        User.builder()
            .status(User.UserStatus.IDLE)
            .createdAt(Instant.now())
            .lastActiveAt(Instant.now())
            .banned(false)
            .build();

    User saved = userRepository.save(user);
    return ResponseEntity.ok(saved);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<User> getUser(@PathVariable String userId) {
    return userRepository
        .findById(userId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{userId}/session")
  public ResponseEntity<Void> updateSession(
      @PathVariable String userId, @RequestParam String sessionId) {
    return userRepository
        .findById(userId)
        .map(
            user -> {
              user.setSessionId(sessionId);
              userRepository.save(user);
              return ResponseEntity.ok().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }
}
