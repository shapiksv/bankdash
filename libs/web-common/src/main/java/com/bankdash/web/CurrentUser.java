package com.bankdash.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated user's id (the access token's {@code sub}) to a {@code UUID} controller parameter.
 *
 * <pre>{@code
 * @GetMapping("/profile/me")
 * ProfileResponse me(@CurrentUser UUID userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {}
