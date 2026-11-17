package br.com.mercadinhos.compartilhado.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "br.com.mercadinhos")
class ErrosApi {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> dadosInvalidos(Exception exception) {
        // Never return rejected values, request bodies or password validation internals.
        var erro = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira os campos e os valores permitidos");
        if (exception instanceof MethodArgumentNotValidException validacao) {
            erro.setProperty("campos", validacao.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField()).distinct().toList());
        }
        return ResponseEntity.badRequest().body(erro);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflito(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Conflito ao gravar; consulte o cadastro e tente novamente"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> arquivoGrande(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.valueOf(413)).body(ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(413), "Arquivo excede o tamanho permitido"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> regra(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
