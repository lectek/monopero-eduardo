package br.com.lojagenerica.platform;

public class ProvisionamentoException extends RuntimeException {

    public ProvisionamentoException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProvisionamentoException(String message) {
        super(message);
    }
}
