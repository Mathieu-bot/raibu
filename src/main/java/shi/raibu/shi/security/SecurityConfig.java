package shi.raibu.shi.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomOidcUserService customOidcUserService;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/ping", "/actuator/**", "/ws/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
                    .defaultSuccessUrl(frontendUrl + "/home", true))
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl(frontendUrl + "/login"));

    return http.build();
  }

  /**
   * API clients get a 401 JSON body; only a browser navigation (explicit Accept: text/html, not an
   * XHR, not a WebSocket handshake) is redirected to the Google login.
   */
  private AuthenticationEntryPoint authenticationEntryPoint() {
    AuthenticationEntryPoint unauthorized =
        (request, response, authException) -> {
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
        };
    AuthenticationEntryPoint googleLogin =
        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google");

    MediaTypeRequestMatcher htmlNavigation =
        new MediaTypeRequestMatcher(new HeaderContentNegotiationStrategy(), MediaType.TEXT_HTML);
    htmlNavigation.setIgnoredMediaTypes(Set.of(MediaType.ALL));
    RequestMatcher xhr = new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest");
    RequestMatcher websocket = new AntPathRequestMatcher("/ws/**");
    RequestMatcher browserNavigation =
        new AndRequestMatcher(
            htmlNavigation, new NegatedRequestMatcher(new OrRequestMatcher(xhr, websocket)));

    LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
    entryPoints.put(browserNavigation, googleLogin);
    DelegatingAuthenticationEntryPoint delegating =
        new DelegatingAuthenticationEntryPoint(entryPoints);
    delegating.setDefaultEntryPoint(unauthorized);
    return delegating;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
