package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import shi.raibu.shi.service.MatchmakingService;

/**
 * Guards the application startup: the listener has a public and a package-private constructor, so
 * Spring only wires it when the public one is annotated with {@code @Autowired}.
 */
class WebSocketPresenceListenerWiringTest {

  @Test
  void spring_can_instantiate_the_listener_as_a_bean() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(MatchmakingService.class, () -> mock(MatchmakingService.class));
      context.register(WebSocketPresenceListener.class);
      context.refresh();

      assertNotNull(context.getBean(WebSocketPresenceListener.class));
    }
  }
}
