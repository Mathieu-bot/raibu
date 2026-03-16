package shi.raibu.shi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${raibu.api.version:1.0.0}")
  private String apiVersion;

  @Bean
  public OpenAPI raibuOpenAPI() {
    Server localServer = new Server();
    localServer.setUrl("http://localhost:8080");
    localServer.setDescription("Local development server");

    Server productionServer = new Server();
    productionServer.setUrl("https://api.raibu.app");
    productionServer.setDescription("Production server");

    Contact contact = new Contact();
    contact.setName("Raibu Team");
    contact.setEmail("contact@raibu.app");
    contact.setUrl("https://raibu.app");

    Info info =
        new Info()
            .title("Raibu API")
            .description(
                "Spring Boot backend for Raibu - Random video chat platform with matchmaking,"
                    + " WebRTC signaling, chat, and moderation features.")
            .version(apiVersion)
            .contact(contact);

    return new OpenAPI().info(info).servers(List.of(localServer, productionServer));
  }
}
