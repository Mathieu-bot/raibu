package shi.raibu.shi.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.SessionFeedback;

@Repository
public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, String> {

  Optional<SessionFeedback> findBySessionIdAndFromUserId(String sessionId, String fromUserId);

  /** Inserts the feedback unless (session, from user) already exists; returns rows inserted. */
  @Modifying
  @Query(
      value =
          "INSERT INTO session_feedback (id, session_id, from_user_id, to_user_id, liked,"
              + " created_at) VALUES (:id, :sessionId, :fromUserId, :toUserId, :liked,"
              + " :createdAt) ON CONFLICT (session_id, from_user_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") String id,
      @Param("sessionId") String sessionId,
      @Param("fromUserId") String fromUserId,
      @Param("toUserId") String toUserId,
      @Param("liked") boolean liked,
      @Param("createdAt") Instant createdAt);
}
