package shi.raibu.shi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security loads the CSRF token lazily, so the XSRF-TOKEN cookie would only be written when
 * something reads the token. Reading it on every request makes sure the cookie is issued on a plain
 * GET (for example {@code GET /me}) and stays in sync with the session.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      // Forces the deferred token to be resolved and the cookie to be written.
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
