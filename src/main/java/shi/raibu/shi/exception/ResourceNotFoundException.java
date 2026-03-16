package shi.raibu.shi.exception;

public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String resource, String id) {
    super(String.format("%s not found with id: %s", resource, id));
  }
}
