package br.com.fiap.ford.journey.infra;

public class IntelligenceUnavailableException extends RuntimeException {
    public IntelligenceUnavailableException(Throwable cause) {
        super("vehicle intelligence is temporarily unavailable", cause);
    }
}
