package com.example.seat_booking_system.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    String errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

    return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + errors);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
    log.warn("[CONFLICT] Request conflict: {}", ex.getMessage());
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    log.warn("[BAD REQUEST] Illegal argument: {}", ex.getMessage());
    return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, Object>> handleNoSuchElementException(
      NoSuchElementException ex) {
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Map<String, Object>> handleBadCredentialsException(
      BadCredentialsException ex) {
    return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
    return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
    log.error("[SERVER ERROR] Unhandled exception occurred: ", ex);
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred. Please try again later.");
  }

  private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", LocalDateTime.now());
    body.put("status", status.value());
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
