package ru.ai.libraryapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений.
 * 
 * Перехватывает и обрабатывает все исключения, возникающие в приложении,
 * возвращая стандартизированные ответы об ошибках.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обработка ошибок валидации входных данных.
     * 
     * @param ex исключение валидации
     * @return ответ с детальным описанием ошибок валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Ошибка валидации: {}", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Обработка всех остальных исключений.
     * 
     * @param ex исключение
     * @return ответ с описанием ошибки
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception ex) {
        log.error("Неожиданная ошибка: {}", ex.getMessage(), ex);
        
        String message = ex.getMessage();
        if (message == null) {
            message = "null";
        }
        
        Map<String, String> error = Map.of("error", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
