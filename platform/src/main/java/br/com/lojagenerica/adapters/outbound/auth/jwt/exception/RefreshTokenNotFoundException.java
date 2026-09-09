package br.com.lojagenerica.adapters.outbound.auth.jwt.exception;

public class RefreshTokenNotFoundException
extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RefreshTokenNotFoundException(String message) {
        super(message);
    }

    public RefreshTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

