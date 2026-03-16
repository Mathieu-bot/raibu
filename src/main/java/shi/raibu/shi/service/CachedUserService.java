package shi.raibu.shi.service;

import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@Service
@AllArgsConstructor
public class CachedUserService {

  private final UserRepository userRepository;

  @Cacheable(value = "users", key = "#userId")
  public Optional<User> getUserById(String userId) {
    return userRepository.findById(userId);
  }

  @Cacheable(value = "users", key = "#provider + ':' + #providerId")
  public Optional<User> getUserByProviderAndProviderId(String provider, String providerId) {
    return userRepository.findByProviderAndProviderId(provider, providerId);
  }

  @Cacheable(value = "users", key = "'email:' + #email")
  public Optional<User> getUserByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  @CacheEvict(value = "users", allEntries = true)
  public void evictAllUsers() {
    // Evict all users from cache
  }

  @CacheEvict(value = "users", key = "#userId")
  public void evictUser(String userId) {
    // Evict specific user from cache
  }
}
