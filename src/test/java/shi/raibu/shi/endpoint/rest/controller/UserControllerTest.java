package shi.raibu.shi.endpoint.rest.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

class UserControllerTest {

  private MockMvc mockMvc;
  private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userRepository)).build();
  }

  @Test
  void returnsOnlyThePublicProfile() throws Exception {
    User user =
        User.builder()
            .id("u1")
            .displayName("Joe")
            .avatarUrl("http://a/pic.png")
            .countryCode("FR")
            .email("secret@example.com")
            .providerId("google-sub")
            .sessionId("ws-1")
            .banned(true)
            .build();
    when(userRepository.findById("u1")).thenReturn(Optional.of(user));

    mockMvc
        .perform(get("/users/u1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u1"))
        .andExpect(jsonPath("$.displayName").value("Joe"))
        .andExpect(jsonPath("$.avatarUrl").value("http://a/pic.png"))
        .andExpect(jsonPath("$.countryCode").value("FR"))
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.providerId").doesNotExist())
        .andExpect(jsonPath("$.sessionId").doesNotExist())
        .andExpect(jsonPath("$.banned").doesNotExist());
  }

  @Test
  void unknownUserIsNotFound() throws Exception {
    when(userRepository.findById("nope")).thenReturn(Optional.empty());

    mockMvc.perform(get("/users/nope")).andExpect(status().isNotFound());
  }

  @Test
  void registerAndSessionEndpointsAreGone() throws Exception {
    mockMvc.perform(post("/users/register")).andExpect(status().is4xxClientError());
    mockMvc
        .perform(put("/users/u1/session").param("sessionId", "x"))
        .andExpect(status().is4xxClientError());
  }
}
