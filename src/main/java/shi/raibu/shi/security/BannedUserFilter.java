package shi.raibu.shi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

/**
 * Rejects every REST call of an authenticated user who is banned, with 403 {"code":"USER_BANNED"}.
 * The ban flag is read with one primary key lookup per request and never cached, so a ban is
 * effective immediately. A few endpoints stay open so the frontend can still show the reason.
 */
@RequiredArgsConstructor
public class BannedUserFilter extends OncePerRequestFilter {

  private static final RequestMatcher ALWAYS_ALLOWED =
      new OrRequestMatcher(
          new AntPathRequestMatcher("/me", HttpMethod.GET.name()),
          new AntPathRequestMatcher("/logout"),
          new AntPathRequestMatcher("/csrf", HttpMethod.GET.name()),
          new AntPathRequestMatcher("/ping"),
          new AntPathRequestMatcher("/login/**"),
          new AntPathRequestMatcher("/oauth2/**"));

  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)
        && !ALWAYS_ALLOWED.matches(request)
        && userRepository.findById(authentication.getName()).map(User::isBanned).orElse(false)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"code\":\"USER_BANNED\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
