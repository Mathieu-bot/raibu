package shi.raibu.shi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = SecurityTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("securitytest")
class CsrfTest {

  @Autowired MockMvc mvc;
  @MockBean CustomOidcUserService customOidcUserService;
  @MockBean shi.raibu.shi.repository.UserRepository userRepository;

  static MockHttpSession loggedInSession(String userId) {
    SecurityContext context = new SecurityContextImpl();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            userId, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    return session;
  }

  @Test
  void postWithoutTokenIsForbidden() throws Exception {
    mvc.perform(post("/echo").session(loggedInSession("u1")))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"code\":\"CSRF_INVALID\"}"));
  }

  @Test
  void plainGetIssuesReadableXsrfCookieThatUnlocksPost() throws Exception {
    MockHttpSession session = loggedInSession("u1");
    MvcResult result =
        mvc.perform(get("/me").session(session)).andExpect(status().isOk()).andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isFalse();

    mvc.perform(
            post("/echo").session(session).cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
        .andExpect(status().isOk());
  }

  @Test
  void wrongHeaderValueIsForbidden() throws Exception {
    MockHttpSession session = loggedInSession("u1");
    Cookie cookie =
        mvc.perform(get("/me").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
    mvc.perform(post("/echo").session(session).cookie(cookie).header("X-XSRF-TOKEN", "forged"))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrfEndpointReturnsTokenForCrossSiteClients() throws Exception {
    MockHttpSession session = loggedInSession("u1");
    mvc.perform(get("/csrf").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void websocketHandshakeIsExemptFromCsrf() throws Exception {
    mvc.perform(post("/ws/info").session(loggedInSession("u1")))
        .andExpect(
            result -> assertThat(List.of(200, 405)).contains(result.getResponse().getStatus()));
  }
}
