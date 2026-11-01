package shi.raibu.shi.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
  Optional<User> findBySessionId(String sessionId);

  List<User> findByStatus(User.UserStatus status);

  List<User> findByStatusAndBannedFalse(User.UserStatus status);

  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  Optional<User> findByEmail(String email);

  /**
   * Locks the given user rows with {@code SELECT ... FOR UPDATE}, always in ascending id order so
   * that two transactions locking the same pair can never deadlock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id in :ids order by u.id")
  List<User> findAllByIdInOrderById(@Param("ids") Collection<String> ids);

  /** Ids (read straight from the database, never from the persistence context) that can match. */
  @Query("select u.id from User u where u.id in :ids and u.status = :status and u.banned = false")
  List<String> findIdsByIdInAndStatusAndBannedFalse(
      @Param("ids") Collection<String> ids, @Param("status") User.UserStatus status);

  @Modifying(flushAutomatically = true)
  @Query(
      "update User u set u.positiveFeedbackCount = u.positiveFeedbackCount + 1,"
          + " u.reputationScore = case when u.reputationScore + 1 > 100 then 100"
          + " else u.reputationScore + 1 end where u.id = :id")
  int applyPositiveFeedback(@Param("id") String id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update User u set u.negativeFeedbackCount = u.negativeFeedbackCount + 1,"
          + " u.reputationScore = case when u.reputationScore - 3 < -100 then -100"
          + " else u.reputationScore - 3 end where u.id = :id")
  int applyNegativeFeedback(@Param("id") String id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update User u set u.strikeCount = u.strikeCount + 1,"
          + " u.reputationScore = case when u.reputationScore - 20 < -100 then -100"
          + " else u.reputationScore - 20 end where u.id = :id")
  int applyActionedReport(@Param("id") String id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update User u set u.reputationScore = case when u.reputationScore > -50 then -50"
          + " else u.reputationScore end where u.id = :id")
  int applyBanReputation(@Param("id") String id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update User u set u.status = :status, u.lastActiveAt = :now where u.id in :ids")
  int updateStatusByIdIn(
      @Param("ids") Collection<String> ids,
      @Param("status") User.UserStatus status,
      @Param("now") Instant now);
}
