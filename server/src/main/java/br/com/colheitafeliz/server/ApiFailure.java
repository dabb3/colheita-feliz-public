package br.com.colheitafeliz.server;

final class ApiFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    ApiFailure(int status, String message) { super(message); this.status = status; }
    int status() { return status; }
}
