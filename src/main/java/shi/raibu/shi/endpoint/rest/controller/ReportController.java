package shi.raibu.shi.endpoint.rest.controller;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.NotificationService;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
public class ReportController {
  private final ReportRepository reportRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  @PostMapping
  public ResponseEntity<Report> createReport(@RequestBody ReportRequest request) {
    Report report =
        Report.builder()
            .reporterId(request.reporterId)
            .reportedUserId(request.reportedUserId)
            .sessionId(request.sessionId)
            .reason(request.reason)
            .description(request.description)
            .createdAt(Instant.now())
            .status(Report.ReportStatus.PENDING)
            .build();

    Report saved = reportRepository.save(report);

    // Auto-ban
    long reportCount = reportRepository.findByReportedUserId(request.reportedUserId).size();
    if (reportCount >= 3) {
      userRepository
          .findById(request.reportedUserId)
          .ifPresent(
              user -> {
                if (!user.isBanned()) {
                  user.setBanned(true);
                  userRepository.save(user);

                  notificationService.notifyUser(
                      user.getId(),
                      NotificationType.USER_BANNED,
                      "Your account has been banned due to multiple reports.",
                      null);
                }
              });
    }

    return ResponseEntity.ok(saved);
  }

  @GetMapping("/pending")
  public ResponseEntity<List<Report>> getPendingReports() {
    return ResponseEntity.ok(reportRepository.findByStatus(Report.ReportStatus.PENDING));
  }

  public record ReportRequest(
      String reporterId,
      String reportedUserId,
      String sessionId,
      Report.ReportReason reason,
      String description) {}
}
