package com.bankdash.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** ProblemDetail shapes shared by every service, so the web app can handle them in one place. */
public final class Problems {

    private Problems() {}

    /** One invalid field. */
    public record FieldError(String field, String message) {}

    /** 400 with an {@code errors} array of {@code {field, message}}, which the web app shows next to each field. */
    public static ProblemDetail validation(List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors.stream()
                .map(error -> Map.of("field", error.field(), "message", error.message()))
                .toList());
        return problem;
    }

    public static ProblemDetail validation(String field, String message) {
        return validation(List.of(new FieldError(field, message)));
    }

    public static ProblemDetail of(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
