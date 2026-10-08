package io.srimaddireddy.crm;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(jakarta.validation.ConstraintViolationException.class) public ResponseEntity<?> invalidParameter(){return ResponseEntity.badRequest().body(Map.of("error","Invalid request parameter"));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("error","Resource already exists"));}
}
