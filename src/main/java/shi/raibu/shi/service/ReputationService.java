package shi.raibu.shi.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shi.raibu.shi.repository.UserRepository;

@Service
@AllArgsConstructor
@Slf4j
public class ReputationService {

  private final UserRepository userRepository;

  public void applyFeedback(String toUserId, boolean liked) {
    userRepository
        .findById(toUserId)
        .ifPresent(
            user -> {
              int score = user.getReputationScore();
              if (liked) {
                user.setPositiveFeedbackCount(user.getPositiveFeedbackCount() + 1);
                score += 1;
              } else {
                user.setNegativeFeedbackCount(user.getNegativeFeedbackCount() + 1);
                score -= 3;
              }
              score = clamp(score);
              user.setReputationScore(score);
              userRepository.save(user);
            });
  }

  public void registerActionedReport(String reportedUserId) {
    userRepository
        .findById(reportedUserId)
        .ifPresent(
            user -> {
              user.setStrikeCount(user.getStrikeCount() + 1);
              int score = clamp(user.getReputationScore() - 20);
              user.setReputationScore(score);
              userRepository.save(user);
            });
  }

  public void registerBan(String userId) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              int score = user.getReputationScore();
              // Ensure heavily negative reputation for banned users
              if (score > -50) {
                score = -50;
              }
              score = clamp(score);
              user.setReputationScore(score);
              userRepository.save(user);
            });
  }

  private int clamp(int score) {
    if (score > 100) {
      return 100;
    }
    if (score < -100) {
      return -100;
    }
    return score;
  }
}
