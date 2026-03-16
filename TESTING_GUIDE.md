# Testing Guide for Raibu Backend Improvements

## Automated Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Suites
```bash
# Service tests
./gradlew test --tests "shi.raibu.shi.service.*Test"

# Exception handling tests
./gradlew test --tests "shi.raibu.shi.exception.*Test"

# Security tests
./gradlew test --tests "shi.raibu.shi.security.*Test"
```

### Test Coverage
- **Current Coverage**: 21.43%
- **Target Coverage**: 70%+

## Manual Testing

### 1. Start the Application
```bash
# Start database
docker compose up -d db

# Set environment variables
export DATABASE_URL='jdbc:postgresql://localhost:55432/raibu'
export DATABASE_USERNAME='raibu_admin'
export DATABASE_PASSWORD='raibu_password'
export PORT='8080'
export ALLOWED_ORIGINS='http://localhost:5173,http://localhost:8080'

# Start backend
./gradlew bootRun
```

### 2. Test OpenAPI/Swagger Documentation

#### Access Swagger UI
```bash
# Open browser
http://localhost:8080/swagger-ui/index.html
```

#### Test Features
1. **Browse API Documentation**
   - Check User Profile endpoints
   - Check Reports endpoints
   - Check Health endpoints

2. **Test Authentication**
   - Click "Authorize" button (lock icon)
   - Login with Google OAuth2
   - Try authenticated endpoints

3. **Try Example Requests**
   - Click "Try it out" on any endpoint
   - Fill in required parameters
   - Click "Execute" to test

### 3. Test Cache Performance

#### Enable Debug Logging
```bash
# In application.properties
logging.level.shi.raibu.shi.service=DEBUG
```

#### Test User Caching
```bash
# First call - cache miss
curl http://localhost:8080/api/users/{userId}

# Second call - cache hit (much faster)
curl http://localhost:8080/api/users/{userId}
```

#### Monitor Cache Behavior
```bash
# Check logs for cache hits/misses
# Look for messages like:
# "Cache hit for user: user-1"
# "Cache miss for user: user-1, loading from database"
```

### 4. Test Admin Security

#### Test Admin Access
```bash
# Login as admin (admin@raibu.app or mathieu@raibu.app)
# Then try:
curl http://localhost:8080/admin/reports

# Should work and return reports
```

#### Test Regular User Access
```bash
# Login as regular user (any other email)
# Then try:
curl http://localhost:8080/admin/reports

# Should return 403 Forbidden with message:
# {
#   "timestamp": "...",
#   "status": 403,
#   "error": "Forbidden",
#   "message": "Insufficient permissions"
# }
```

#### Test Unauthenticated Access
```bash
# Without authentication:
curl http://localhost:8080/admin/reports

# Should redirect to OAuth2 login
```

### 5. Test Exception Handling

#### Test Validation Errors
```bash
# Invalid country code
curl -X PUT http://localhost:8080/me/country \
  -H "Content-Type: application/json" \
  -d '{"countryCode": "INVALID"}'

# Should return 400 with validation error
```

#### Test Resource Not Found
```bash
curl http://localhost:8080/api/users/non-existent-user

# Should return 404 with error response:
# {
#   "timestamp": "...",
#   "status": 404,
#   "error": "Not Found",
#   "message": "User not found with id: non-existent-user"
# }
```

### 6. Test Health Endpoints

#### Basic Health Check
```bash
curl http://localhost:8080/health

# Should return:
# {
#   "status": "UP",
#   "service": "raibu-backend",
#   "timestamp": "..."
# }
```

#### Liveness Probe
```bash
curl http://localhost:8080/health/live

# Should return: {"status": "alive"}
```

#### Readiness Probe
```bash
curl http://localhost:8080/health/ready

# Should return: {"status": "ready"}
```

## Integration Testing

### Test WebSocket/STOMP with Enhanced Features

#### Connect with Admin User
```javascript
// Connect as admin
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

// Use admin credentials (admin@raibu.app)
stompClient.connect({}, function(frame) {
    // Should connect successfully

    // Try admin operations
    stompClient.subscribe('/topic/admin', function(message) {
        // Should receive admin messages
    });
});
```

### Test Caching Under Load

#### Load Testing Script
```bash
# Install Apache Bench if needed
# sudo apt-get install apache2-utils

# Test user endpoint performance
ab -n 1000 -c 10 http://localhost:8080/api/users/test-user-id

# First run: cache misses (slower)
# Second run: cache hits (much faster)
```

## Performance Benchmarks

### Before Caching
```
Average response time: 50-200ms (database queries)
Database load: High
Scalability: Limited
```

### After Caching
```
Average response time: 1-5ms (cache hits)
Database load: Reduced significantly
Scalability: Improved
```

## Troubleshooting

### Common Issues

#### 1. Cache Not Working
**Symptoms**: No performance improvement, still slow
**Solutions**:
- Check that `@EnableCaching` is present
- Verify cache annotations are correct
- Enable debug logging for cache

#### 2. Admin Security Not Working
**Symptoms**: Regular users can access admin endpoints
**Solutions**:
- Verify `@EnableAspectJAutoProxy` is enabled
- Check that `@RequireRole` annotations are present
- Verify admin email configuration

#### 3. Swagger Not Accessible
**Symptoms**: 404 on Swagger UI
**Solutions**:
- Check SpringDoc dependency is in classpath
- Verify application is running
- Check for port conflicts

#### 4. Tests Failing
**Symptoms**: Unit tests failing after changes
**Solutions**:
- Update test dependencies
- Check for missing mock dependencies
- Verify test data is valid

## Verification Checklist

### Functionality
- [ ] All automated tests pass
- [ ] Swagger UI is accessible
- [ ] Admin endpoints are protected
- [ ] Cache improves performance
- [ ] Health endpoints work
- [ ] Exception handling works correctly

### Performance
- [ ] Cache hits reduce response time
- [ ] Database load is reduced
- [ ] Memory usage is acceptable

### Security
- [ ] Admin endpoints require proper role
- [ ] Regular users cannot access admin functions
- [ ] Error messages don't leak sensitive information

### Documentation
- [ ] Swagger documentation is complete
- [ ] All guides are updated
- [ ] Examples are clear and accurate

## Next Steps

After successful testing:
1. Deploy to staging environment
2. Run integration tests
3. Monitor performance metrics
4. Gather user feedback
5. Plan production rollout
