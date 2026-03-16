package shi.raibu.shi.exception;

public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }

  public ForbiddenException(String resource, String action, String reason) {
    super(String.format("Cannot %s %s: %s", action, resource, reason));
  }
}
