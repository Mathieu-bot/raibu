package shi.raibu.shi.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@SpringBootTest(classes = SecurityTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("securitytest")
class BannedUserFilterTest {

  @Autowired MockMvc mvc;
  @MockBean CustomOidcUserService customOidcUserService;
  @MockBean UserRepository userRepository;

  @BeforeEach
  void users() {
    when(userRepository.findById("banned"))
        .thenReturn(Optional.of(User.builder().id("banned").banned(true).build()));
    when(userRepository.findById("ok"))
        .thenReturn(Optional.of(User.builder().id("ok").banned(false).build()));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
    return request -> {
      request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "t"));
      request.addHeader("X-XSRF-TOKEN", "t");
      return request;
    };
  }

  @Test
  void bannedUserIsRejectedOnRestCalls() throws Exception {
    mvc.perform(
            post("/friends/x/messages").session(CsrfTest.loggedInSession("banned")).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"code\":\"USER_BANNED\"}"));
    mvc.perform(get("/ws/info").session(CsrfTest.loggedInSession("banned")))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"code\":\"USER_BANNED\"}"));
  }

  @Test
  void bannedUserCanStillReadMeAndCsrfAndPing() throws Exception {
    mvc.perform(get("/me").session(CsrfTest.loggedInSession("banned"))).andExpect(status().isOk());
    mvc.perform(get("/csrf").session(CsrfTest.loggedInSession("banned")))
        .andExpect(status().isOk());
    mvc.perform(get("/ping").session(CsrfTest.loggedInSession("banned")))
        .andExpect(status().isOk());
  }

  @Test
  void normalUserIsUnaffected() throws Exception {
    mvc.perform(post("/friends/x/messages").session(CsrfTest.loggedInSession("ok")).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void unknownUserIsNotBlockedByTheFilter() throws Exception {
    mvc.perform(get("/ws/info").session(CsrfTest.loggedInSession("ghost")))
        .andExpect(status().isOk());
  }
}
