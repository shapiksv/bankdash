package com.bankdash.auth.api;

import com.bankdash.auth.app.InvalidCredentialsException;
import com.bankdash.auth.app.InvalidRefreshTokenException;
import com.bankdash.auth.app.UnknownUserException;
import com.bankdash.auth.app.WrongCurrentPasswordException;
import com.bankdash.auth.domain.EmailAlreadyRegisteredException;
import com.bankdash.auth.domain.PasswordPolicy.InvalidPasswordException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns exceptions into RFC 9457 {@link ProblemDetail} responses. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private final RefreshCookies cookies;

    public ApiExceptionHandler(RefreshCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", List.of(Map.of("field", ex.field(), "message", ex.getMessage())));
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(WrongCurrentPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", List.of(Map.of("field", "currentPassword", "message", ex.getMessage())));
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(UnknownUserException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Unknown user");
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "An account with this email already exists");
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    /** 401 and a cleared cookie, so the browser stops sending a token that will never work again. */
    @ExceptionHandler
    ResponseEntity<ProblemDetail> handle(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Session expired");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(problem);
    }
}
