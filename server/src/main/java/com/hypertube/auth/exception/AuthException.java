package com.hypertube.auth.exception;

public class AuthException extends RuntimeException {
    private final String code;
    private final boolean clearRefreshCookie;

    public AuthException(String code, String message, boolean clearRefreshCookie) {
        super(message);
        this.code = code;
        this.clearRefreshCookie = clearRefreshCookie;
    }
    public String getCode() { return code; }
    public boolean shouldClearRefreshCookie() { return clearRefreshCookie; }
}
