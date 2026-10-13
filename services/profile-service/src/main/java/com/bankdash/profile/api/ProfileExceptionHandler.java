package com.bankdash.profile.api;

import com.bankdash.profile.domain.ProfileNotFoundException;
import com.bankdash.profile.domain.UsernameTakenException;
import com.bankdash.web.Problems;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Domain exceptions as ProblemDetail; web-common handles validation and the rest. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProfileExceptionHandler {

    /** The web app retries this one for a moment after signup, while the user-registered event is on its way. */
    @ExceptionHandler
    ProblemDetail handle(ProfileNotFoundException ex) {
        return Problems.of(HttpStatus.NOT_FOUND, "Profile not found", "The profile does not exist yet");
    }

    @ExceptionHandler
    ProblemDetail handle(UsernameTakenException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "Username taken", ex.getMessage());
        problem.setProperty("errors", List.of(Map.of("field", "username", "message", "This username is already taken")));
        return problem;
    }
}
