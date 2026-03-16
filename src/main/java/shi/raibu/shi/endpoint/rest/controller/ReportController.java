package shi.raibu.shi.endpoint.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shi.raibu.shi.endpoint.rest.dto.CreateReportDto;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.NotificationService;
import shi.raibu.shi.service.ReputationService;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
@Tag(name = "Reports", description = "Endpoints for creating and managing user reports")
@SecurityRequirement(name = "oauth2")
public class ReportController {
  private final ReportRepository reportRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final ReputationService reputationService;

  @PostMapping
  @Operation(
      summary = "Create a new report",
      description =
          "Creates a new report against another user. Auto-bans if user reaches 3 reports.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report created successfully",
            content = @Content(schema = @Schema(implementation = Report.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
      })
  public ResponseEntity<Report> createReport(@Valid @RequestBody CreateReportDto request) {
    Report report =
        Report.builder()
            .reporterId(request.getReporterId())
            .reportedUserId(request.getReportedUserId())
            .sessionId(request.getSessionId())
            .reason(request.getReason())
            .description(request.getDescription())
            .createdAt(Instant.now())
            .status(Report.ReportStatus.PENDING)
            .build();

    Report saved = reportRepository.save(report);

    // Auto-ban
    long reportCount = reportRepository.findByReportedUserId(request.getReportedUserId()).size();
    if (reportCount >= 3) {
      userRepository
          .findById(request.getReportedUserId())
          .ifPresent(
              user -> {
                if (!user.isBanned()) {
                  user.setBanned(true);
                  userRepository.save(user);

                  reputationService.registerBan(user.getId());

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
  @Operation(
      summary = "Get pending reports",
      description = "Returns all reports with PENDING status")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "List of pending reports retrieved successfully")
      })
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
