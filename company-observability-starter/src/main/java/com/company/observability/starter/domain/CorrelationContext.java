package com.company.observability.starter.domain;


/**
 * Predstavlja rezultat razresavanja correlation ID vrednosti.
 *
 * @param correlationId finalna vrednost correlation ID-a
 *
 */

public record CorrelationContext(String correlationId) {
    public CorrelationContext {
        if(correlationId == null || correlationId.isBlank()){
            throw new IllegalArgumentException("Correlation ID ne sme biti null ili prazan");
        }
    }
}
