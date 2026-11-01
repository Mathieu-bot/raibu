package shi.raibu.shi.endpoint.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import shi.raibu.shi.endpoint.rest.dto.CreateReportDto;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.model.Role;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.security.RequireRole;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
@Tag(name = "Reports", description = "Endpoints for creating and managing user reports")
@SecurityRequirement(name = "oauth2")
public class ReportController {
  private final ReportRepository reportRepository;
  private final UserRepository userRepository;
  private final ChatSessionRepository chatSessionRepository;

  @PostMapping
  @Operation(
      summary = "Create a new report",
      description =
          "Creates a report against another user. The author is the authenticated user. Reports"
              + " never ban automatically: moderators review them.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report created successfully",
            content = @Content(schema = @Schema(implementation = Report.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Reported user or session not found"),
        @ApiResponse(responseCode = "409", description = "Already reported")
      })
  @Transactional
  public ResponseEntity<Report> createReport(
      Principal principal, @Valid @RequestBody CreateReportDto request) {
    String reporterId = principal.getName();
    String reportedUserId = request.getReportedUserId();

    if (reporterId.equals(reportedUserId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot report yourself");
    }
    if (!userRepository.existsById(reportedUserId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reported user not found");
    }

    String sessionId =
        request.getSessionId() == null || request.getSessionId().isBlank()
            ? null
            : request.getSessionId();
    if (sessionId != null) {
      ChatSession session =
          chatSessionRepository
              .findById(sessionId)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
      Set<String> participants = new HashSet<>();
      participants.add(session.getUser1Id());
      participants.add(session.getUser2Id());
      if (!participants.contains(reporterId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You were not in this session");
      }
      if (!participants.contains(reportedUserId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "The reported user was not in this session");
      }
    }

    if (reportRepository.existsByReporterIdAndReportedUserIdAndSessionId(
        reporterId, reportedUserId, sessionId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reported");
    }

    Report report =
        Report.builder()
            .reporterId(reporterId)
            .reportedUserId(reportedUserId)
            .sessionId(sessionId)
            .reason(request.getReason())
            .description(request.getDescription())
            .createdAt(Instant.now())
            .status(Report.ReportStatus.PENDING)
            .build();

    try {
      return ResponseEntity.ok(reportRepository.saveAndFlush(report));
    } catch (DataIntegrityViolationException e) {
      // Concurrent duplicate caught by the unique index
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reported");
    }
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
  @RequireRole(Role.ADMIN)
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
