package com.vibevault.orderservice.advices;

import com.vibevault.orderservice.dtos.exceptions.ExceptionDto;
import com.vibevault.orderservice.exceptions.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvices {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ExceptionDto> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), "ORDER_NOT_FOUND");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), "VALIDATION_ERROR");
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ExceptionDto> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Order was modified concurrently, please retry", request.getRequestURI(), "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionDto> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI(), "INTERNAL_ERROR");
    }

    private ResponseEntity<ExceptionDto> buildResponse(HttpStatus status, String message, String path, String errorCode) {
        ExceptionDto dto = new ExceptionDto(
                status.toString(),
                Encode.forHtml(message),
                Encode.forHtml(path),
                errorCode,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(dto, status);
    }
}
