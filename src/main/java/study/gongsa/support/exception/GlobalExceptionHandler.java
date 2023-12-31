package study.gongsa.support.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import study.gongsa.dto.DefaultResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // message
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity exception(Exception error){
        log.info("{}: {}",error.getClass().getName(), error.getMessage());

        DefaultResponse responseBody = new DefaultResponse(null, error.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
    }

    // status, location, message
    @ExceptionHandler(value = IllegalStateExceptionWithLocation.class)
    public ResponseEntity illegalStateException(IllegalStateExceptionWithLocation error){
        log.info("{}: {}",error.getClass().getName(), error.getMessage());
        DefaultResponse responseBody = new DefaultResponse(error.getLocation(),error.getMessage());
        return ResponseEntity.status(error.getStatus()).body(responseBody);
    }


    // request validation error
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity methodArgumentNotValidException(MethodArgumentNotValidException error){
        log.info("{}: {}",error.getClass().getName(), error.getMessage());

        ObjectError firstError = error.getAllErrors().get(0);
        String errorField = firstError.getCodes()[1].split("\\.")[1];
        String errorMessage = firstError.getDefaultMessage();

        DefaultResponse responseBody = new DefaultResponse(errorField,errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
    }

}
