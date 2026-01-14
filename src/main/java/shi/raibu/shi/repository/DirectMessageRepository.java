package shi.raibu.shi.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.DirectMessage;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, String> {

  List<DirectMessage> findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderBySentAtAsc(
      String senderId1, String recipientId1, String senderId2, String recipientId2);
}
