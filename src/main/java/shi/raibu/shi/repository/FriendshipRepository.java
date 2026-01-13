package shi.raibu.shi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.Friendship;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, String> {

  Optional<Friendship> findByUserId1AndUserId2(String userId1, String userId2);

  List<Friendship> findByUserId1OrUserId2(String userId1, String userId2);
}
