package shi.raibu.shi.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.SessionFeedback;

@Repository
public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, String> {

  Optional<SessionFeedback> findBySessionIdAndFromUserId(String sessionId, String fromUserId);
}
