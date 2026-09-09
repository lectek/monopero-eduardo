package br.com.lojagenerica.adapters.outbound.auth.jwt.exception;

public class TokenGenerationException
extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TokenGenerationException(String message) {
        super(message);
    }

    public TokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

