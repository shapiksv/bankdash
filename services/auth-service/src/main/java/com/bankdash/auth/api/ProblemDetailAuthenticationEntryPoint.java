package com.bankdash.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 401 as a ProblemDetail, keeping the {@code WWW-Authenticate: Bearer ...} header. */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final JsonMapper jsonMapper;

    public ProblemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        bearer.commence(request, response, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid access token");
        problem.setTitle("Unauthorized");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
