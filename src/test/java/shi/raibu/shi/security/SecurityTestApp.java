package shi.raibu.shi.security;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal, offline application used by the web security tests: the real {@link SecurityConfig} on a
 * real servlet container, no database and no network call to Google.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      JpaRepositoriesAutoConfiguration.class,
      FlywayAutoConfiguration.class
    })
@Import({SecurityConfig.class, CsrfController.class, SecurityTestApp.Probe.class})
class SecurityTestApp {

  @RestController
  static class Probe {
    @GetMapping({"/ping", "/me", "/ws/info", "/swagger-ui/index.html", "/v3/api-docs"})
    String get() {
      return "ok";
    }

    @PostMapping({"/echo", "/friends/{id}/messages"})
    String post() {
      return "ok";
    }
  }
}
