package com.company.observability.starter.domain;

import java.util.UUID;

/**
 * Podrazumevana implementacija {@link CorrelationIdGenerator} interfejsa
 * koja generise correlation ID vrednosti koristeci UUID format.
 */
public class UuidCorrelationIdGenerator implements CorrelationIdGenerator {

    /**
     * Generise novu correlation ID vrednost.
     *
     * @return nova UUID correlation ID vrednost
     */
    public String generate(){
        return UUID.randomUUID().toString();
    }
}
