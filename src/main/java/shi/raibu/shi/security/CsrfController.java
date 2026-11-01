package shi.raibu.shi.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the CSRF token in the body. A single page app served from another site cannot read the
 * XSRF-TOKEN cookie of the API, so it calls this endpoint and sends the value back in the header.
 */
@RestController
public class CsrfController {

  public record CsrfResponse(String headerName, String parameterName, String token) {}

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
  }
}
