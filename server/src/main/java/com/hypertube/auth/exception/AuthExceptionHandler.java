package com.hypertube.auth.exception;

import com.hypertube.auth.security.AuthCookies;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class AuthExceptionHandler extends ResponseEntityExceptionHandler {
    private final AuthCookies cookies;
    public AuthExceptionHandler(AuthCookies cookies) { this.cookies = cookies; }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> authFailure(AuthException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getCode(), exception.getMessage(),
                exception.shouldClearRefreshCookie() ? cookies.clear() : new HttpHeaders());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "A valid access-token cookie is required", new HttpHeaders());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "The request is not authorized or its CSRF token is invalid", new HttpHeaders());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpHeaders headers) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).headers(headers).body(body);
    }
}
