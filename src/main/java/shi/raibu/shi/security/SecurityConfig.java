package shi.raibu.shi.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomOidcUserService customOidcUserService;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/ping", "/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2.userInfoEndpoint(
                    userInfo -> userInfo.oidcUserService(customOidcUserService)))
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/"));

    return http.build();
  }
}
