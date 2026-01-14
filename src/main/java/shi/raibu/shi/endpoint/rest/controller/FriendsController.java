package shi.raibu.shi.endpoint.rest.controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.DirectMessage;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.repository.FriendshipRepository;
import shi.raibu.shi.service.DirectMessageService;

@RestController
@RequestMapping("/friends")
@AllArgsConstructor
public class FriendsController {

  private final FriendshipRepository friendshipRepository;
  private final DirectMessageService directMessageService;

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

  @GetMapping("/{friendId}/messages")
  public ResponseEntity<List<DirectMessageResponse>> getMessages(
      Principal principal, @PathVariable String friendId) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();

    try {
      List<DirectMessage> messages = directMessageService.getConversation(userId, friendId);
      List<DirectMessageResponse> response =
          messages.stream()
              .map(
                  m ->
                      new DirectMessageResponse(
                          m.getId(),
                          m.getSenderId(),
                          m.getRecipientId(),
                          m.getContent(),
                          m.getSentAt()))
              .collect(Collectors.toList());
      return ResponseEntity.ok(response);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(403).build();
    }
  }

  @PostMapping("/{friendId}/messages")
  public ResponseEntity<DirectMessageResponse> sendMessage(
      Principal principal,
      @PathVariable String friendId,
      @RequestBody SendDirectMessageRequest request) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();

    try {
      DirectMessage message = directMessageService.sendMessage(userId, friendId, request.content());
      DirectMessageResponse response =
          new DirectMessageResponse(
              message.getId(),
              message.getSenderId(),
              message.getRecipientId(),
              message.getContent(),
              message.getSentAt());
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.status(403).build();
    }
  }

  public record FriendResponse(String userId, Instant createdAt) {}

  public record DirectMessageResponse(
      String id, String senderId, String recipientId, String content, Instant sentAt) {}

  public record SendDirectMessageRequest(String content) {}
}
