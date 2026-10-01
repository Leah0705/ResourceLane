package io.resourcelane.wallet_service.exceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler to intercept and manage exceptions across the entire Wallet Service application.
 * This class provides centralized exception handling across all @RequestMapping methods through @ExceptionHandler methods,
 * ensuring consistent error responses for the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation errors triggered by @Valid annotations on request payload fields.
     * Extracts field-specific error messages and constructs a detailed 400 Bad Request response.
     *
     * @param ex The MethodArgumentNotValidException thrown when validation on an argument annotated with @Valid fails.
     * @return A map containing field names as keys and corresponding validation error messages as values, 
     *         wrapped in a ResponseEntity with HTTP status 400 (Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Handles generic and unhandled exceptions that propagate up to the controller layer.
     * Prevents stack traces from being exposed to the client by wrapping the error in a standard response format.
     *
     * @param e The unhandled Exception that was thrown during request processing.
     * @return A map containing a generic "error" key and the exception's message, 
     *         wrapped in a ResponseEntity with HTTP status 500 (Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}
