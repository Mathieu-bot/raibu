package shi.raibu.shi.service;

import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.repository.FriendshipRepository;

@Service
@AllArgsConstructor
public class CachedFriendshipService {

  private final FriendshipRepository friendshipRepository;

  @Cacheable(value = "friends", key = "'user:' + #userId")
  public List<Friendship> getFriendsByUserId(String userId) {
    return friendshipRepository.findByUserId1OrUserId2(userId, userId);
  }

  @Cacheable(value = "friends", key = "'pair:' + #userId1 + ':' + #userId2")
  public Optional<Friendship> getFriendship(String userId1, String userId2) {
    String user1 = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
    String user2 = userId1.compareTo(userId2) < 0 ? userId2 : userId1;
    return friendshipRepository.findByUserId1AndUserId2(user1, user2);
  }

  @CacheEvict(value = "friends", allEntries = true)
  public void evictAllFriendships() {
    // Evict all friendships from cache
  }

  @CacheEvict(value = "friends", key = "'user:' + #userId")
  public void evictUserFriends(String userId) {
    // Evict friendships for specific user
  }
}
