package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Guards the application startup: the interceptor has a public and a package-private constructor,
 * so Spring only wires it when the public one is annotated with {@code @Autowired}.
 */
class RateLimitingChannelInterceptorWiringTest {

  @Test
  void spring_can_instantiate_the_interceptor_as_a_bean() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class));
      context.register(RateLimitingChannelInterceptor.class);
      context.refresh();

      assertNotNull(context.getBean(RateLimitingChannelInterceptor.class));
    }
  }
}
