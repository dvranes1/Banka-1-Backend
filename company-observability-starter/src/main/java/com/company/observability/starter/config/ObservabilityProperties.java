package com.company.observability.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguraciona svojstva za observability starter biblioteku.
 * <p>
 * Svojstva se ucitavaju iz konfiguracije aplikacije preko prefiksa
 * {@code company.observability.starter}.
 */
@ConfigurationProperties(prefix = "company.observability.starter")
public class ObservabilityProperties {
    private boolean enabled = true;
    private String correlationHeaderName = "X-Correlation-Id";
    private boolean userIdMdcEnabled = false;

    /**
     * Vraca da li je company observability starter ukljucen.
     *
     * @return {@code true} ako je starter ukljucen, inace {@code false}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Podesava da li je company observability starter ukljucen.
     *
     * @param enabled nova vrednost za ukljucivanje ili iskljucivanje startera
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Vraca naziv HTTP zaglavlja koje se koristi za correlation ID.
     *
     * @return naziv correlation ID zaglavlja
     */
    public String getCorrelationHeaderName() {
        return correlationHeaderName;
    }

    /**
     * Podesava naziv HTTP zaglavlja koje se koristi za correlation ID.
     *
     * @param correlationHeaderName naziv correlation ID zaglavlja
     */
    public void setCorrelationHeaderName(String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName;
    }

    /**
     * Vraca da li je upis user ID vrednosti u MDC ukljucen.
     *
     * @return {@code true} ako je user ID MDC logika ukljucena, inace {@code false}
     */
    public boolean isUserIdMdcEnabled() {
        return userIdMdcEnabled;
    }

    /**
     * Vraca da li je upis user ID vrednosti u MDC ukljucen.
     *
     * @return {@code true} ako je user ID MDC logika ukljucena, inace {@code false}
     */
    public void setUserIdMdcEnabled(boolean userIdMdcEnabled) {
        this.userIdMdcEnabled = userIdMdcEnabled;
    }

}
