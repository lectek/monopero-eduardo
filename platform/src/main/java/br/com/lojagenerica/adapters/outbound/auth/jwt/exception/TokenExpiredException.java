package br.com.lojagenerica.adapters.outbound.auth.jwt.exception;

public class TokenExpiredException
extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

