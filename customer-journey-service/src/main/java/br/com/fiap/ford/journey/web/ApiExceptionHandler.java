package br.com.fiap.ford.journey.web;

import br.com.fiap.ford.journey.application.JourneyNotFoundException;
import br.com.fiap.ford.journey.domain.DomainException;
import br.com.fiap.ford.journey.infra.IntelligenceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(JourneyNotFoundException.class)
    ProblemDetail notFound(JourneyNotFoundException error, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Journey not found", error.getMessage(), request);
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException error, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", error.getMessage(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = error.getBindingResult().getFieldErrors().stream()
                .map(item -> item.getField() + ": " + item.getDefaultMessage())
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return super.handleExceptionInternal(error,
                ApiProblems.create(status, "Invalid request", detail, path(request)), headers, status, request);
    }

    @ExceptionHandler(IntelligenceUnavailableException.class)
    ProblemDetail unavailable(IntelligenceUnavailableException error, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable", error.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException error, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permissions.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception error, HttpServletRequest request) {
        logger.error("Unhandled API error", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String title = HttpStatus.valueOf(status.value()).getReasonPhrase();
        ProblemDetail source = body instanceof ProblemDetail existing ? existing
                : error instanceof ErrorResponse response ? response.getBody() : null;
        String detail = source != null && source.getDetail() != null ? source.getDetail() : title;
        return super.handleExceptionInternal(error,
                ApiProblems.create(status, title, detail, path(request)), headers, status, request);
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        return ApiProblems.create(status, title, detail, request.getRequestURI());
    }
}
