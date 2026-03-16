package shi.raibu.shi.endpoint.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import shi.raibu.shi.model.Report;

public class CreateReportDto {

  @NotBlank(message = "Reporter ID is required")
  private String reporterId;

  @NotBlank(message = "Reported user ID is required")
  private String reportedUserId;

  private String sessionId;

  @NotNull(message = "Reason is required")
  private Report.ReportReason reason;

  @Size(max = 1000, message = "Description must be less than 1000 characters")
  private String description;

  public String getReporterId() {
    return reporterId;
  }

  public void setReporterId(String reporterId) {
    this.reporterId = reporterId;
  }

  public String getReportedUserId() {
    return reportedUserId;
  }

  public void setReportedUserId(String reportedUserId) {
    this.reportedUserId = reportedUserId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public Report.ReportReason getReason() {
    return reason;
  }

  public void setReason(Report.ReportReason reason) {
    this.reason = reason;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
