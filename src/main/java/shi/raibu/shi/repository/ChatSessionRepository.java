package shi.raibu.shi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.ChatSession;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
  List<ChatSession> findByUser1IdOrUser2Id(String user1Id, String user2Id);

  Optional<ChatSession> findByUser1IdAndUser2IdAndStatus(
      String user1Id, String user2Id, ChatSession.SessionStatus status);
}
