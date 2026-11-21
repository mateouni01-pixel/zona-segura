package com.zonasegura.zona_segura_api;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Respuestas de negocio legibles, sin devolver consultas, credenciales ni excepciones internas. */
@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail business(ResponseStatusException exception) {
        String detail = exception.getReason() == null ? "No se pudo completar la operación." : exception.getReason();
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalidInput(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los tipos y campos del formulario.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Los datos ya cambiaron o tienen registros relacionados. Actualiza antes de reintentar.");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail concurrentChange(ConcurrencyFailureException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Otra operación está modificando estos datos. Reintenta conservando la misma solicitud.");
    }
}
