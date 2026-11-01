package shi.raibu.shi.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.ChatSession;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
  List<ChatSession> findByUser1IdOrUser2Id(String user1Id, String user2Id);

  List<ChatSession> findByUser1IdOrUser2Id(String user1Id, String user2Id, Pageable pageable);

  Optional<ChatSession> findByUser1IdAndUser2IdAndStatus(
      String user1Id, String user2Id, ChatSession.SessionStatus status);

  @Query(
      "select s from ChatSession s where s.status = :status"
          + " and (s.user1Id in :userIds or s.user2Id in :userIds)")
  List<ChatSession> findByStatusAndUserIds(
      @Param("status") ChatSession.SessionStatus status,
      @Param("userIds") Collection<String> userIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ChatSession s where s.id = :id")
  Optional<ChatSession> findByIdForUpdate(@Param("id") String id);

  default List<ChatSession> findActiveByUserIds(Collection<String> userIds) {
    return findByStatusAndUserIds(ChatSession.SessionStatus.ACTIVE, userIds);
  }
}
