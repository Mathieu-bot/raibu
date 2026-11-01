package shi.raibu.shi.endpoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shi.raibu.shi.endpoint.rest.dto.FlaggedUserDto;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.NotificationService;
import shi.raibu.shi.service.ReputationService;

class AdminModerationControllerTest {

  private ReportRepository reportRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reportRepository = mock(ReportRepository.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AdminModerationController(
                    reportRepository,
                    mock(UserRepository.class),
                    mock(ChatSessionRepository.class),
                    mock(NotificationService.class),
                    mock(ReputationService.class)))
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
}
