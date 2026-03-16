package shi.raibu.shi.endpoint.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

  @GetMapping
  @Operation(summary = "Health check", description = "Basic health check endpoint that returns the API status")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "API is healthy")
  })
  public ResponseEntity<Map<String, String>> healthCheck() {
    Map<String, String> response = new HashMap<>();
    response.put("status", "UP");
    response.put("service", "raibu-backend");
    response.put("timestamp", java.time.Instant.now().toString());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/live")
  @Operation(summary = "Liveness probe", description = "Kubernetes liveness probe endpoint")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Service is alive")
  })
  public ResponseEntity<Map<String, String>> liveness() {
    Map<String, String> response = new HashMap<>();
    response.put("status", "alive");
    return ResponseEntity.ok(response);
  }

  @GetMapping("/ready")
  @Operation(summary = "Readiness probe", description = "Kubernetes readiness probe endpoint")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Service is ready to accept traffic")
  })
  public ResponseEntity<Map<String, String>> readiness() {
    Map<String, String> response = new HashMap<>();
    response.put("status", "ready");
    return ResponseEntity.ok(response);
  }
}
