package shi.raibu.shi.endpoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.Report;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.ReportRepository;
import shi.raibu.shi.repository.UserRepository;

class ReportControllerTest {

  private ReportRepository reportRepository;
  private UserRepository userRepository;
  private ChatSessionRepository chatSessionRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reportRepository = mock(ReportRepository.class);
    userRepository = mock(UserRepository.class);
    chatSessionRepository = mock(ChatSessionRepository.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ReportController(reportRepository, userRepository, chatSessionRepository))
            .build();
    when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(i -> i.getArgument(0));
  }

  private MockHttpServletRequestBuilder report(String json) {
    return post("/reports")
        .principal(() -> "reporter-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Test
  void usesThePrincipalAsAuthorAndIgnoresTheBodyReporterId() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);

    mockMvc
        .perform(
            report(
                "{\"reporterId\":\"someone-else\",\"reportedUserId\":\"target-1\","
                    + "\"reason\":\"SPAM\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reporterId").value("reporter-1"));

    ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).saveAndFlush(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("reporter-1", captor.getValue().getReporterId());
  }

  @Test
  void neverBansAutomatically() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);

    mockMvc
        .perform(report("{\"reportedUserId\":\"target-1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isOk());

    verify(userRepository, never()).save(any());
  }

  @Test
  void rejectsSelfReport() throws Exception {
    mockMvc
        .perform(report("{\"reportedUserId\":\"reporter-1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isBadRequest());
    verify(reportRepository, never()).saveAndFlush(any());
  }

  @Test
  void rejectsUnknownReportedUser() throws Exception {
    when(userRepository.existsById("ghost")).thenReturn(false);

    mockMvc
        .perform(report("{\"reportedUserId\":\"ghost\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnknownSession() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(chatSessionRepository.findById("s1")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            report("{\"reportedUserId\":\"target-1\",\"sessionId\":\"s1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsSessionWithoutTheReporter() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(chatSessionRepository.findById("s1"))
        .thenReturn(
            Optional.of(ChatSession.builder().id("s1").user1Id("a").user2Id("target-1").build()));

    mockMvc
        .perform(
            report("{\"reportedUserId\":\"target-1\",\"sessionId\":\"s1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsSessionWithoutTheReportedUser() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(chatSessionRepository.findById("s1"))
        .thenReturn(
            Optional.of(ChatSession.builder().id("s1").user1Id("reporter-1").user2Id("x").build()));

    mockMvc
        .perform(
            report("{\"reportedUserId\":\"target-1\",\"sessionId\":\"s1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void acceptsSessionContainingBothUsers() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(chatSessionRepository.findById("s1"))
        .thenReturn(
            Optional.of(
                ChatSession.builder().id("s1").user1Id("target-1").user2Id("reporter-1").build()));

    mockMvc
        .perform(
            report("{\"reportedUserId\":\"target-1\",\"sessionId\":\"s1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("s1"));
  }

  @Test
  void duplicateReportIsAConflict() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(reportRepository.existsByReporterIdAndReportedUserIdAndSessionId(
            "reporter-1", "target-1", null))
        .thenReturn(true);

    mockMvc
        .perform(report("{\"reportedUserId\":\"target-1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isConflict());
    verify(reportRepository, never()).saveAndFlush(any());
  }

  @Test
  void concurrentDuplicateCaughtByTheIndexIsAConflict() throws Exception {
    when(userRepository.existsById("target-1")).thenReturn(true);
    when(reportRepository.saveAndFlush(any(Report.class)))
        .thenThrow(new DataIntegrityViolationException("uq_report_reporter_reported_session"));

    mockMvc
        .perform(report("{\"reportedUserId\":\"target-1\",\"reason\":\"SPAM\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void reportedUserIdIsRequired() throws Exception {
    mockMvc.perform(report("{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());
  }
}
