package shi.raibu.shi.endpoint.rest.controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.repository.FriendshipRepository;

@RestController
@RequestMapping("/friends")
@AllArgsConstructor
public class FriendsController {

  private final FriendshipRepository friendshipRepository;

  @GetMapping
  public ResponseEntity<List<FriendResponse>> getFriends(Principal principal) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();

    List<Friendship> friendships = friendshipRepository.findByUserId1OrUserId2(userId, userId);

    List<FriendResponse> friends =
        friendships.stream()
            .map(
                f -> {
                  String otherId = userId.equals(f.getUserId1()) ? f.getUserId2() : f.getUserId1();
                  Instant createdAt = f.getCreatedAt();
                  return new FriendResponse(otherId, createdAt);
                })
            .collect(Collectors.toList());

    return ResponseEntity.ok(friends);
  }

  public record FriendResponse(String userId, Instant createdAt) {}
}
