package shi.raibu.shi.websocket;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  /** Maximum size of one STOMP message, in bytes. */
  static final int MESSAGE_SIZE_LIMIT = 64 * 1024;

  private final RateLimitingChannelInterceptor rateLimitingInterceptor;

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue");
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(rateLimitingInterceptor);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String[] origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
  }
}
