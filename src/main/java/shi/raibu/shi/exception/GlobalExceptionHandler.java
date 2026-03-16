package shi.raibu.shi.exception;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<ErrorResponse> handleBadRequestExceptions(
      Exception ex, WebRequest request) {
    log.warn("Bad request: {}", ex.getMessage());
    return ResponseEntity.badRequest()
        .body(buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST));
  }

  @ExceptionHandler({ResourceNotFoundException.class})
  public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    log.warn("Resource not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND));
  }

  @ExceptionHandler({ForbiddenException.class})
  public ResponseEntity<ErrorResponse> handleForbiddenException(
      ForbiddenException ex, WebRequest request) {
    log.warn("Forbidden: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(buildErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN));
  }

  @ExceptionHandler({Exception.class})
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
    log.error("Unexpected error occurred", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(buildErrorResponse("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR));
  }

  private ErrorResponse buildErrorResponse(String message, HttpStatus status) {
    return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message);
  }

  public record ErrorResponse(Instant timestamp, int status, String error, String message) {}
}
