package br.com.lojagenerica.adapters.inbound.web.dto;

import java.time.Instant;

/** DTO mutável para respostas padronizadas de erro. */
public class ProblemDetails {
    private int status;
    private String error;
    private String message;
    private String path;
    private Instant timestamp;

    public ProblemDetails() { }

    public ProblemDetails(int status, String error, String message, String path, Instant timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = timestamp;
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}