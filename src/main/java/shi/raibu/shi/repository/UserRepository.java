package shi.raibu.shi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
  Optional<User> findBySessionId(String sessionId);

  List<User> findByStatus(User.UserStatus status);

  List<User> findByStatusAndBannedFalse(User.UserStatus status);

  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  Optional<User> findByEmail(String email);
}
