# Cache Strategy Documentation

## Overview

Raibu backend implements a multi-layer caching strategy to optimize performance and reduce database load.

## Cache Configuration

### Cache Manager
- **Implementation**: ConcurrentMapCacheManager
- **Type**: In-memory cache (suitable for single-instance deployment)
- **Configuration**: `CacheConfig.java`

### Cache Regions
```java
ConcurrentMapCacheManager(
    "users",           // User data
    "userPreferences", // User preferences
    "friends",         // Friendship data
    "reputations",     // Reputation scores
    "notifications"    // Notification data
)
```

## Cached Services

### 1. CachedUserService
Handles user-related data caching:

```java
@Cacheable(value = "users", key = "#userId")
public Optional<User> getUserById(String userId)

@Cacheable(value = "users", key = "#provider + ':' + #providerId")
public Optional<User> getUserByProviderAndProviderId(String provider, String providerId)

@Cacheable(value = "users", key = "'email:' + #email")
public Optional<User> getUserByEmail(String email)
```

**Cache Keys:**
- By ID: `users::{userId}`
- By provider: `users::{provider}:{providerId}`
- By email: `users::email:{email}`

### 2. CachedFriendshipService
Handles friendship and social graph caching:

```java
@Cacheable(value = "friends", key = "'user:' + #userId")
public List<Friendship> getFriendsByUserId(String userId)

@Cacheable(value = "friends", key = "'pair:' + #userId1 + ':' + #userId2")
public Optional<Friendship> getFriendship(String userId1, String userId2)
```

**Cache Keys:**
- User friends: `friends::user:{userId}`
- Friendship pair: `friends::pair:{userId1}:{userId2}`

### 3. CachedReputationService
Handles reputation score caching:

```java
@Cacheable(value = "reputations", key = "#userId")
public ReputationData getReputationScore(String userId)
```

**Cache Keys:**
- Reputation: `reputations::{userId}`

## Cache Eviction

### Automatic Eviction
Cache entries are automatically evicted when:
- Data is modified (CREATE/UPDATE/DELETE operations)
- Explicit eviction is triggered
- Memory limits are reached (if configured)

### Manual Eviction
```java
// Evict specific entry
@CacheEvict(value = "users", key = "#userId")
public void evictUser(String userId)

// Evict all entries in cache region
@CacheEvict(value = "users", allEntries = true)
public void evictAllUsers()
```

## Integration with Existing Services

### FriendshipService Integration
```java
@Service
public class FriendshipService {
    private final CachedFriendshipService cachedFriendshipService;

    public Friendship createFriendshipIfAbsent(String userIdA, String userIdB) {
        // ... create friendship logic ...

        // Evict cache for both users
        cachedFriendshipService.evictUserFriends(userIdA);
        cachedFriendshipService.evictUserFriends(userIdB);

        return saved;
    }
}
```

### ReputationService Integration
```java
@Service
public class ReputationService {
    private final CachedReputationService cachedReputationService;

    public void applyFeedback(String toUserId, boolean liked) {
        // ... update reputation logic ...

        // Evict cache for affected user
        cachedReputationService.evictUserReputation(toUserId);
    }
}
```

## Performance Benefits

### Before Caching
- Database query per user lookup: ~50-200ms
- Repeated queries for same user data
- High database load during peak usage

### After Caching
- Cache hit: ~1-5ms (10-50x faster)
- Reduced database load
- Better scalability

## Cache Strategy Considerations

### Current Implementation
- **Pros**: Simple, fast, no external dependencies
- **Cons**: In-memory only, not distributed

### Future Enhancements
1. **Redis Integration**: For distributed caching
2. **TTL Configuration**: Time-based expiration
3. **Cache Warming**: Pre-load frequently accessed data
4. **Metrics**: Cache hit/miss monitoring

## Best Practices

1. **Always evict cache after data modifications**
2. **Use consistent cache key patterns**
3. **Consider cache size limits for production**
4. **Monitor cache effectiveness**
5. **Test cache behavior in integration tests**

## Monitoring Cache Performance

### Metrics to Track
- Cache hit ratio
- Cache miss ratio
- Average response time
- Database query reduction

### Example Metrics
```java
@Timed(value = "cache.users.hit", description = "User cache hits")
@Cacheable(value = "users", key = "#userId")
public Optional<User> getUserById(String userId) {
    // ...
}
```

## Configuration

### Application Properties
```properties
# Cache configuration (future enhancement)
spring.cache.type=simple
spring.cache.cache-names=users,userPreferences,friends,reputations,notifications
spring.cache.cache-max-size=1000
```

### Custom Cache Configuration
```java
@Bean
public CacheManager cacheManager() {
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(Arrays.asList(
        new ConcurrentMapCache("users", CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build().asMap(), false)
    ));
    return cacheManager;
}
```

## Testing

### Testing Cached Services
```java
@SpringBootTest
class CachedUserServiceTest {

    @Autowired
    private CachedUserService cachedUserService;

    @Test
    void shouldCacheUserById() {
        // First call - cache miss
        Optional<User> first = cachedUserService.getUserById("user-1");

        // Second call - cache hit
        Optional<User> second = cachedUserService.getUserById("user-1");

        // Verify same instance returned (cached)
        assertSame(first.get(), second.get());
    }
}
```
