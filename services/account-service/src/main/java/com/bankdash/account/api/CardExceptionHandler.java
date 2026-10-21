package com.bankdash.account.api;

import com.bankdash.account.domain.CardAlreadyBlockedException;
import com.bankdash.account.domain.CardNotFoundException;
import com.bankdash.account.domain.CardNotUsableException;
import com.bankdash.account.domain.WeakPinException;
import com.bankdash.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Domain exceptions as ProblemDetail; web-common handles validation and the rest. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CardExceptionHandler {

    /** The same answer whether the card is missing or someone else's, so card ids reveal nothing. */
    @ExceptionHandler
    ProblemDetail handle(CardNotFoundException ex) {
        return Problems.of(HttpStatus.NOT_FOUND, "Card not found", "The card does not exist");
    }

    @ExceptionHandler
    ProblemDetail handle(CardAlreadyBlockedException ex) {
        return Problems.of(HttpStatus.CONFLICT, "Card already blocked", "The card is already blocked");
    }

    @ExceptionHandler
    ProblemDetail handle(CardNotUsableException ex) {
        return Problems.of(HttpStatus.CONFLICT, "Card not usable", ex.getMessage());
    }

    /** {@code rule} names the broken rule (e.g. CONSECUTIVE_DIGITS), so the web app can explain it. */
    @ExceptionHandler
    ProblemDetail handle(WeakPinException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "Weak PIN", ex.getMessage());
        problem.setProperty("rule", ex.rule().name());
        return problem;
    }
}
