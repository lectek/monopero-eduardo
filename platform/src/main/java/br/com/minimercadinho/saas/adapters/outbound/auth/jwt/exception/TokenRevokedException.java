package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception;

public class TokenRevokedException
extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TokenRevokedException(String message) {
        super(message);
    }

    public TokenRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}

