package shi.raibu.shi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Needs a real servlet container: the native forwarded headers strategy lives in Tomcat. */
@SpringBootTest(
    classes = SecurityTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("securitytest")
class ForwardedHeadersTest {

  @LocalServerPort int port;
  @MockBean CustomOidcUserService customOidcUserService;

  private String redirectUriSentToGoogle(boolean behindProxy) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/oauth2/authorization/google"))
            .header("Accept", "text/html");
    if (behindProxy) {
      request
          .header("X-Forwarded-Proto", "https")
          .header("X-Forwarded-Host", "api.example.org")
          .header("X-Forwarded-Port", "443");
    }
    HttpResponse<String> response =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(302);
    String location = response.headers().firstValue("Location").orElseThrow();
    assertThat(location).startsWith("https://accounts.google.com/");
    String raw =
        Arrays.stream(URI.create(location).getRawQuery().split("&"))
            .filter(p -> p.startsWith("redirect_uri="))
            .findFirst()
            .orElseThrow()
            .substring("redirect_uri=".length());
    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
  }

  @Test
  void buildsHttpsRedirectUriBehindTlsTerminatingProxy() throws Exception {
    assertThat(redirectUriSentToGoogle(true))
        .isEqualTo("https://api.example.org/login/oauth2/code/google");
  }

  @Test
  void keepsPlainHttpWithoutForwardedHeaders() throws Exception {
    assertThat(redirectUriSentToGoogle(false))
        .isEqualTo("http://localhost:" + port + "/login/oauth2/code/google");
  }
}
