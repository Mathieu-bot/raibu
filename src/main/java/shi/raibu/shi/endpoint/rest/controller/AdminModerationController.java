package shi.raibu.shi.endpoint.rest.controller;

import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.model.Report.ReportStatus;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.NotificationService;

@RestController
@RequestMapping("/admin")
@AllArgsConstructor
public class AdminModerationController {

  private final ReportRepository reportRepository;
  private final UserRepository userRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final NotificationService notificationService;

  @GetMapping("/reports")
  public ResponseEntity<List<Report>> getReports(Report.ReportStatus status) {
    if (status != null) {
      return ResponseEntity.ok(reportRepository.findByStatus(status));
    }
    return ResponseEntity.ok(reportRepository.findAll());
  }

  @GetMapping("/users/{userId}/reports")
  public ResponseEntity<List<Report>> getReportsForUser(@PathVariable String userId) {
    return ResponseEntity.ok(reportRepository.findByReportedUserId(userId));
  }

  @PutMapping("/reports/{reportId}/status")
  public ResponseEntity<Void> updateReportStatus(
      @PathVariable String reportId, @RequestBody UpdateReportStatusRequest request) {
    return reportRepository
        .findById(reportId)
        .map(
            report -> {
              report.setStatus(request.status());
              reportRepository.save(report);

              if (request.status() == ReportStatus.ACTIONED && report.getReporterId() != null) {
                String dataJson = String.format("{\"reportId\":\"%s\"}", report.getId());
                notificationService.notifyUser(
                    report.getReporterId(),
                    NotificationType.REPORT_ACTIONED,
                    "One of your reports has been processed.",
                    dataJson);
              }

              return ResponseEntity.ok().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/users/{userId}/sessions")
  public ResponseEntity<List<ChatSession>> getUserSessions(@PathVariable String userId) {
    return ResponseEntity.ok(chatSessionRepository.findByUser1IdOrUser2Id(userId, userId));
  }

  @PutMapping("/users/{userId}/ban")
  public ResponseEntity<Void> updateUserBan(
      @PathVariable String userId, @RequestBody UpdateUserBanRequest request) {
    return userRepository
        .findById(userId)
        .map(
            user -> {
              boolean wasBanned = user.isBanned();
              boolean nowBanned = request.banned();

              user.setBanned(nowBanned);
              userRepository.save(user);

              if (!wasBanned && nowBanned) {
                notificationService.notifyUser(
                    userId, NotificationType.USER_BANNED, "Your account has been banned.", null);
              } else if (wasBanned && !nowBanned) {
                notificationService.notifyUser(
                    userId,
                    NotificationType.USER_UNBANNED,
                    "Your account has been unbanned.",
                    null);
              }
              return ResponseEntity.ok().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  public record UpdateReportStatusRequest(Report.ReportStatus status) {}

  public record UpdateUserBanRequest(boolean banned) {}
}
