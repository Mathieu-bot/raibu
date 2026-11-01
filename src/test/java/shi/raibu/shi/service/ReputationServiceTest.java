package shi.raibu.shi.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CachedReputationService cachedReputationService;

  private ReputationService reputationService;

  @BeforeEach
  void setUp() {
    reputationService = new ReputationService(userRepository, cachedReputationService);
  }

  @Test
  @DisplayName("like uses the atomic update and evicts the cache, never a read-modify-write")
  void likeIsAtomic() {
    when(userRepository.applyPositiveFeedback("u")).thenReturn(1);

    reputationService.applyFeedback("u", true);

    verify(userRepository).applyPositiveFeedback("u");
    verify(cachedReputationService).evictUserReputation("u");
    verify(userRepository, never()).save(any());
    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("dislike uses the atomic negative update")
  void dislikeIsAtomic() {
    when(userRepository.applyNegativeFeedback("u")).thenReturn(1);

    reputationService.applyFeedback("u", false);

    verify(userRepository).applyNegativeFeedback("u");
    verify(cachedReputationService).evictUserReputation("u");
  }

  @Test
  @DisplayName("unknown user does not evict the cache")
  void unknownUser() {
    when(userRepository.applyNegativeFeedback("ghost")).thenReturn(0);

    reputationService.applyFeedback("ghost", false);

    verifyNoInteractions(cachedReputationService);
  }

  @Test
  @DisplayName("actioned report and ban use atomic updates")
  void reportAndBan() {
    when(userRepository.applyActionedReport("u")).thenReturn(1);
    when(userRepository.applyBanReputation("u")).thenReturn(1);

    reputationService.registerActionedReport("u");
    reputationService.registerBan("u");

    verify(userRepository).applyActionedReport("u");
    verify(userRepository).applyBanReputation("u");
    verify(cachedReputationService, times(2)).evictUserReputation("u");
  }
}
