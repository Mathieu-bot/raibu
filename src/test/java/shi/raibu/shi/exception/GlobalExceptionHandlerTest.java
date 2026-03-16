package shi.raibu.shi.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler exceptionHandler;
  private WebRequest webRequest;

  @BeforeEach
  void setUp() {
    exceptionHandler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    webRequest = new ServletWebRequest(request);
  }

  @Nested
  @DisplayName("handleBadRequestExceptions()")
  class HandleBadRequestExceptionsTests {

    @Test
    @DisplayName("should return BAD_REQUEST for IllegalArgumentException")
    void shouldReturnBadRequestForIllegalArgumentException() {
      IllegalArgumentException exception = new IllegalArgumentException("Invalid input parameter");

      var response = exceptionHandler.handleBadRequestExceptions(exception, webRequest);

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
      assertEquals("Invalid input parameter", response.getBody().message());
    }

    @Test
    @DisplayName("should return BAD_REQUEST for IllegalStateException")
    void shouldReturnBadRequestForIllegalStateException() {
      IllegalStateException exception = new IllegalStateException("Invalid state for operation");

      var response = exceptionHandler.handleBadRequestExceptions(exception, webRequest);

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
      assertEquals("Invalid state for operation", response.getBody().message());
    }
  }

  @Nested
  @DisplayName("handleResourceNotFoundException()")
  class HandleResourceNotFoundExceptionTests {

    @Test
    @DisplayName("should return NOT_FOUND for ResourceNotFoundException")
    void shouldReturnNotFoundForResourceNotFoundException() {
      ResourceNotFoundException exception = new ResourceNotFoundException("User", "123");

      var response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().status());
      assertTrue(response.getBody().message().contains("User"));
      assertTrue(response.getBody().message().contains("123"));
    }
  }

  @Nested
  @DisplayName("handleForbiddenException()")
  class HandleForbiddenExceptionTests {

    @Test
    @DisplayName("should return FORBIDDEN for ForbiddenException")
    void shouldReturnForbiddenForForbiddenException() {
      ForbiddenException exception = new ForbiddenException("User not authorized");

      var response = exceptionHandler.handleForbiddenException(exception, webRequest);

      assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
      assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().status());
      assertEquals("User not authorized", response.getBody().message());
    }
  }

  @Nested
  @DisplayName("handleGenericException()")
  class HandleGenericExceptionTests {

    @Test
    @DisplayName("should return INTERNAL_SERVER_ERROR for generic exceptions")
    void shouldReturnInternalServerErrorForGenericExceptions() {
      Exception exception = new Exception("Unexpected database error");

      var response = exceptionHandler.handleGenericException(exception, webRequest);

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().status());
      assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    @DisplayName("should return INTERNAL_SERVER_ERROR for NullPointerException")
    void shouldReturnInternalServerErrorForNullPointerException() {
      NullPointerException exception = new NullPointerException("Null reference encountered");

      var response = exceptionHandler.handleGenericException(exception, webRequest);

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().status());
      assertEquals("An unexpected error occurred", response.getBody().message());
    }
  }

  @Nested
  @DisplayName("ErrorResponse structure")
  class ErrorResponseStructureTests {

    @Test
    @DisplayName("should contain timestamp in error response")
    void shouldContainTimestampInErrorResponse() {
      ResourceNotFoundException exception = new ResourceNotFoundException("User", "123");

      var response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      assertNotNull(response.getBody().timestamp());
    }

    @Test
    @DisplayName("should contain status code in error response")
    void shouldContainStatusCodeInErrorResponse() {
      ResourceNotFoundException exception = new ResourceNotFoundException("User", "123");

      var response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().status());
    }

    @Test
    @DisplayName("should contain error phrase in error response")
    void shouldContainErrorPhraseInErrorResponse() {
      ResourceNotFoundException exception = new ResourceNotFoundException("User", "123");

      var response = exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      assertEquals("Not Found", response.getBody().error());
    }
  }
}
