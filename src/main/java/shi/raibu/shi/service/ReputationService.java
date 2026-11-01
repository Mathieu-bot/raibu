package shi.raibu.shi.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shi.raibu.shi.repository.UserRepository;

/**
 * Reputation changes are single atomic SQL updates (read-modify-write inside the database), so
 * concurrent feedbacks, reports or bans on the same user can never overwrite each other. The score
 * stays within [-100, 100].
 */
@Service
@AllArgsConstructor
@Slf4j
public class ReputationService {

  private final UserRepository userRepository;
  private final CachedReputationService cachedReputationService;

  @Transactional
  public void applyFeedback(String toUserId, boolean liked) {
    int updated =
        liked
            ? userRepository.applyPositiveFeedback(toUserId)
            : userRepository.applyNegativeFeedback(toUserId);
    if (updated > 0) {
      cachedReputationService.evictUserReputation(toUserId);
    }
  }

  @Transactional
  public void registerActionedReport(String reportedUserId) {
    if (userRepository.applyActionedReport(reportedUserId) > 0) {
      cachedReputationService.evictUserReputation(reportedUserId);
    }
  }

  @Transactional
  public void registerBan(String userId) {
    if (userRepository.applyBanReputation(userId) > 0) {
      cachedReputationService.evictUserReputation(userId);
    }
  }
}
