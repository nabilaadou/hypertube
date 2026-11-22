package com.hypertube.auth.exception;

public class AuthException extends RuntimeException {
    private final String code;
    private final boolean clearAuthCookies;

    public AuthException(String code, String message, boolean clearAuthCookies) {
        super(message);
        this.code = code;
        this.clearAuthCookies = clearAuthCookies;
    }
    public String getCode() { return code; }
    public boolean shouldClearAuthCookies() { return clearAuthCookies; }
}
