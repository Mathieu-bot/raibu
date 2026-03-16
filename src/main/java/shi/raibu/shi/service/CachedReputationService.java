package shi.raibu.shi.service;

import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import shi.raibu.shi.repository.UserRepository;

@Service
@AllArgsConstructor
public class CachedReputationService {

  private final UserRepository userRepository;

  @Cacheable(value = "reputations", key = "#userId")
  public ReputationData getReputationScore(String userId) {
    return userRepository.findById(userId)
        .map(user -> new ReputationData(
            user.getReputationScore(),
            user.getPositiveFeedbackCount(),
            user.getNegativeFeedbackCount(),
            user.getStrikeCount()
        ))
        .orElse(null);
  }

  @CacheEvict(value = "reputations", key = "#userId")
  public void evictUserReputation(String userId) {
    // Evict reputation for specific user
  }

  @CacheEvict(value = "reputations", allEntries = true)
  public void evictAllReputations() {
    // Evict all reputations from cache
  }

  public record ReputationData(
      int score,
      int positiveCount,
      int negativeCount,
      int strikeCount
  ) {}
}
