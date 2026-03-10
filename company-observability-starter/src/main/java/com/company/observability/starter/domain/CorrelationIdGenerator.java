package com.company.observability.starter.domain;


/**
 * Interfejs za generisanje correlation ID vrednosti.
 * <p>
 * Koristi se kao apstrakcija iznad konkretnih implementacija generatora
 * kako bi biblioteka mogla da podrzi razlicite formate correlation ID-a.
 */
public interface CorrelationIdGenerator {

    /**
     * Generise novu correlation ID vrednost.
     *
     * @return nova correlation ID vrednost
     */
    String generate();
}
