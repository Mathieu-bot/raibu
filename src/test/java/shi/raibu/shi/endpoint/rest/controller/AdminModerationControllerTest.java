package shi.raibu.shi.endpoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shi.raibu.shi.endpoint.rest.dto.FlaggedUserDto;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.MatchmakingService;
import shi.raibu.shi.service.NotificationService;
import shi.raibu.shi.service.ReputationService;

class AdminModerationControllerTest {

  private ReportRepository reportRepository;
  private UserRepository userRepository;
  private MatchmakingService matchmakingService;
  private ReputationService reputationService;
  private NotificationService notificationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reportRepository = mock(ReportRepository.class);
    userRepository = mock(UserRepository.class);
    matchmakingService = mock(MatchmakingService.class);
    reputationService = mock(ReputationService.class);
    notificationService = mock(NotificationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AdminModerationController(
                    reportRepository,
                    userRepository,
                    mock(ChatSessionRepository.class),
                    notificationService,
                    reputationService,
                    matchmakingService))
            .build();
  }

  @Test
  void flaggedUsersUseDefaultsOfThreeReportersOverSevenDays() throws Exception {
    Instant last = Instant.parse("2026-11-01T10:00:00Z");
    when(reportRepository.findFlaggedUsers(any(Instant.class), eq(3L)))
        .thenReturn(List.of(new FlaggedUserDto("u1", 4, 5, last)));

    mockMvc
        .perform(get("/admin/reports/flagged"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value("u1"))
        .andExpect(jsonPath("$[0].distinctReporters").value(4))
        .andExpect(jsonPath("$[0].reportCount").value(5))
        .andExpect(jsonPath("$[0].lastReportAt").exists());

    ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
    verify(reportRepository).findFlaggedUsers(since.capture(), eq(3L));
    long days = Duration.between(since.getValue(), Instant.now()).toDays();
    org.junit.jupiter.api.Assertions.assertTrue(days == 6 || days == 7);
  }

  @Test
  void flaggedUsersParametersAreBounded() throws Exception {
    when(reportRepository.findFlaggedUsers(any(Instant.class), eq(1L))).thenReturn(List.of());

    mockMvc
        .perform(get("/admin/reports/flagged").param("minReporters", "-5").param("days", "100000"))
        .andExpect(status().isOk());

    verify(reportRepository).findFlaggedUsers(any(Instant.class), eq(1L));
  }

  private void ban(boolean alreadyBanned, boolean banned) throws Exception {
    when(userRepository.findById("u1"))
        .thenReturn(Optional.of(User.builder().id("u1").banned(alreadyBanned).build()));
    mockMvc
        .perform(
            put("/admin/users/u1/ban")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"banned\":" + banned + "}"))
        .andExpect(status().isOk());
  }

  @Test
  void banEndsTheActiveChatOnce() throws Exception {
    ban(false, true);
    verify(matchmakingService, times(1)).endChat("u1");
  }

  @Test
  void reBanDoesNotEndChatAgain() throws Exception {
    ban(true, true);
    verify(matchmakingService, never()).endChat("u1");
  }

  @Test
  void unbanDoesNotEndChat() throws Exception {
    ban(true, false);
    verify(matchmakingService, never()).endChat("u1");
  }

  private Report report(Report.ReportStatus current) {
    Report report =
        Report.builder().id("r1").reporterId("a").reportedUserId("b").status(current).build();
    when(reportRepository.findById("r1")).thenReturn(Optional.of(report));
    return report;
  }

  private void putStatus(String json, int expected) throws Exception {
    mockMvc
        .perform(
            put("/admin/reports/r1/status").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().is(expected));
  }

  @Test
  void nullReportStatusIsRejected() throws Exception {
    report(Report.ReportStatus.PENDING);
    putStatus("{\"status\":null}", 400);
    verify(reportRepository, never()).save(any());
  }

  @Test
  void actionedTransitionAppliesStrikeAndNotificationOnce() throws Exception {
    report(Report.ReportStatus.PENDING);
    putStatus("{\"status\":\"ACTIONED\"}", 200);
    verify(reputationService, times(1)).registerActionedReport("b");
    verify(notificationService, times(1)).notifyUser(eq("a"), any(), any(), any());
  }

  @Test
  void resendingTheSameStatusDoesNothing() throws Exception {
    report(Report.ReportStatus.ACTIONED);
    putStatus("{\"status\":\"ACTIONED\"}", 200);
    verify(reportRepository, never()).save(any());
    verify(reputationService, never()).registerActionedReport(any());
    verify(notificationService, never()).notifyUser(any(), any(), any(), any());
  }

  @Test
  void reviewingAReportHasNoSideEffects() throws Exception {
    report(Report.ReportStatus.PENDING);
    putStatus("{\"status\":\"REVIEWED\"}", 200);
    verify(reportRepository, times(1)).save(any());
    verify(reputationService, never()).registerActionedReport(any());
  }
}
