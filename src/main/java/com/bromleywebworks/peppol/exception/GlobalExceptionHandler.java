package com.bromleywebworks.peppol.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingIdentifierException.class)
    public ResponseEntity<Map<String, Object>> handleMissingIdentifier(MissingIdentifierException ex) {
        log.warn("Missing identifier: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("status", "missing_identifier");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("Required request part missing: {}", ex.getRequestPartName());
        Map<String, Object> error = new HashMap<>();
        error.put("status", "invalid_file");
        error.put("message", "Required file part '" + ex.getRequestPartName() + "' is not present");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("status", "invalid_file");
        error.put("message", "File size exceeds the maximum allowed upload size (10MB)");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
