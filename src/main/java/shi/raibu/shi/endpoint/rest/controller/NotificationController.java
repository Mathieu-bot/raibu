package shi.raibu.shi.endpoint.rest.controller;

import java.security.Principal;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.service.NotificationService;
import shi.raibu.shi.websocket.model.NotificationPayload;

@RestController
@RequestMapping("/notifications")
@AllArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @GetMapping
  public ResponseEntity<List<NotificationPayload>> getNotifications(
      Principal principal,
      @RequestParam(value = "unread", required = false) Boolean unread,
      @RequestParam(value = "limit", required = false) Integer limit) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();
    boolean onlyUnread = unread != null && unread;
    int effectiveLimit = limit != null ? limit : 50;

    List<NotificationPayload> notifications =
        notificationService.getNotifications(userId, onlyUnread, effectiveLimit);

    return ResponseEntity.ok(notifications);
  }

  @PutMapping("/{notificationId}/read")
  public ResponseEntity<Void> markAsRead(Principal principal, @PathVariable String notificationId) {
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }

    String userId = principal.getName();
    boolean updated = notificationService.markAsRead(userId, notificationId);

    if (!updated) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok().build();
  }
}
