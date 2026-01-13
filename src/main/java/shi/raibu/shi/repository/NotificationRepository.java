package shi.raibu.shi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

  Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);
}
