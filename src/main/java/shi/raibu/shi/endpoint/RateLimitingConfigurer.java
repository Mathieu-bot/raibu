package shi.raibu.shi.endpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import shi.raibu.shi.service.RateLimiterService;

@Configuration
@AllArgsConstructor
public class RateLimitingConfigurer implements WebMvcConfigurer {

  private final RateLimiterService rateLimiterService;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new RateLimitingInterceptor(rateLimiterService));
  }

  @AllArgsConstructor
  private static class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
      String path = request.getRequestURI();

      if ("/ping".equals(path) || path.startsWith("/actuator") || path.startsWith("/ws")) {
        return true;
      }

      String method = request.getMethod();

      boolean isWrite = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);

      // We apply stricter limits on write endpoints that can be abused (reports, user updates).
      boolean isSensitivePath =
          path.startsWith("/reports") || path.startsWith("/me") || path.startsWith("/users");

      int limit;
      long windowMs = 60_000L;

      if (isWrite && isSensitivePath) {
        // e.g. max 30 write operations per minute per user/IP
        limit = 30;
      } else {
        // broader limit for all other requests
        limit = 300;
      }

      String principalId =
          request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
      String ip = request.getRemoteAddr();

      String key = principalId != null ? "USER:" + principalId : "IP:" + ip;

      if (!rateLimiterService.isAllowed(key, limit, windowMs)) {
        response.setStatus(429); // Too Many Requests
        return false;
      }

      return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        @Nullable Exception ex)
        throws Exception {
      // no-op
    }
  }
}
