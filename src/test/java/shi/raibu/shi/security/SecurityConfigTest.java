package shi.raibu.shi.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Behavior of the real security filter chain, offline (see {@link SecurityTestApp}). */
@SpringBootTest(classes = SecurityTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("securitytest")
class SecurityConfigTest {

  @Autowired MockMvc mvc;
  @MockBean CustomOidcUserService customOidcUserService;
  @MockBean shi.raibu.shi.repository.UserRepository userRepository;

  @Test
  void jsonRequestWithoutSessionGets401() throws Exception {
    mvc.perform(get("/me").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().json("{\"code\":\"UNAUTHENTICATED\"}"));
  }

  @Test
  void requestWithoutAcceptHeaderGets401() throws Exception {
    mvc.perform(get("/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void xhrRequestAcceptingHtmlGets401() throws Exception {
    mvc.perform(get("/me").accept(MediaType.TEXT_HTML).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void browserNavigationIsRedirectedToGoogleLogin() throws Exception {
    mvc.perform(get("/me").accept(MediaType.TEXT_HTML))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern("**/oauth2/authorization/google"));
  }

  @Test
  void pingIsPublic() throws Exception {
    mvc.perform(get("/ping")).andExpect(status().isOk());
  }

  @Test
  void websocketEndpointRequiresAuthentication() throws Exception {
    mvc.perform(get("/ws/info")).andExpect(status().isUnauthorized());
    mvc.perform(get("/ws/info").session(CsrfTest.loggedInSession("u1"))).andExpect(status().isOk());
  }

  @Test
  void swaggerAndApiDocsRequireAuthentication() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void actuatorIsNoLongerPermitted() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void corsAllowsConfiguredOriginOnly() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/me")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void oauth2AuthorizationEndpointStillRedirectsToGoogle() throws Exception {
    mvc.perform(get("/oauth2/authorization/google").accept(MediaType.TEXT_HTML))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));
  }
}
