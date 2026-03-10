package com.company.observability.starter.config;

import com.company.observability.starter.domain.CorrelationIdGenerator;
import com.company.observability.starter.domain.UserIdExtractor;
import com.company.observability.starter.domain.UuidCorrelationIdGenerator;

import com.company.observability.starter.service.CorrelationIdService;
import com.company.observability.starter.service.ExceptionLoggingService;
import com.company.observability.starter.service.JwtAuthenticationUserIdExtractor;
import com.company.observability.starter.service.RequestLoggingService;
import com.company.observability.starter.service.SensitiveDataMaskingService;
import com.company.observability.starter.service.UserIdMdcService;
import com.company.observability.starter.web.GlobalExceptionHandler;
import com.company.observability.starter.web.filter.CorrelationIdFilter;
import com.company.observability.starter.web.filter.HttpRequestLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.Optional;

/**
 * Glavna auto-konfiguraciona klasa observability starter biblioteke.
 * <p>
 * Ova konfiguracija registruje bean-ove potrebne za:
 * correlation ID obradu, MDC podrsku za user ID, HTTP request logging,
 * maskiranje osetljivih podataka i globalno hvatanje neocekivanih izuzetaka.
 * <p>
 * Konfiguracija se aktivira samo u servlet web aplikacijama, kada je
 * {@link OncePerRequestFilter} dostupan na classpath-u i kada je svojstvo
 * {@code company.observability.starter.enabled} postavljeno na {@code true}
 * ili nije eksplicitno definisano.
 */

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "company.observability.starter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfig {

    /**
     * Registruje podrazumevani generator correlation ID vrednosti.
     * <p>
     * Ako aplikacija ne obezbedi sopstveni {@link CorrelationIdGenerator},
     * koristi se UUID implementacija.
     *
     * @return podrazumevani generator correlation ID vrednosti
     */
    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdGenerator correlationIdGenerator() {
        return new UuidCorrelationIdGenerator();
    }

    /**
     * Registruje servis za razresavanje correlation ID vrednosti.
     *
     * @param correlationIdGenerator generator koji se koristi za kreiranje
     *                               novih correlation ID vrednosti kada one
     *                               nisu prisutne u dolaznom zahtevu
     * @return servis za razresavanje correlation ID vrednosti
     */
    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdService correlationIdService(CorrelationIdGenerator correlationIdGenerator) {
        return new CorrelationIdService(correlationIdGenerator);
    }

    /**
     * Registruje podrazumevani {@link UserIdExtractor} koji ne vraca nijednu vrednost.
     * <p>
     * Ovaj bean se koristi samo ako aplikacija ili dodatna security konfiguracija
     * nisu vec obezbedile konkretnu implementaciju.
     *
     * @return podrazumevani extractor koji vraca prazan {@link Optional}
     */
    @Bean
    @ConditionalOnMissingBean(UserIdExtractor.class)
    @ConditionalOnMissingClass("org.springframework.security.core.Authentication")
    public UserIdExtractor defaultUserIdExtractor() {
        return Optional::empty;
    }

    /**
     * Registruje servis za upis i uklanjanje user ID vrednosti iz MDC konteksta.
     *
     * @param userIdExtractor komponenta za izdvajanje user ID vrednosti
     * @param observabilityProperties konfiguraciona svojstva biblioteke
     * @return servis za rad sa user ID vrednoscu u MDC-u
     */
    @Bean
    @ConditionalOnMissingBean
    public UserIdMdcService userIdMdcService(
            UserIdExtractor userIdExtractor,
            ObservabilityProperties observabilityProperties
    ) {
        return new UserIdMdcService(userIdExtractor, observabilityProperties);
    }


    /**
     * Registruje filter zaduzen za correlation ID obradu.
     * <p>
     * Filter cita correlation ID iz zaglavlja, generise novu vrednost ako je potrebno,
     * upisuje je u MDC i vraca je kroz response header.
     *
     * @param correlationIdService servis za razresavanje correlation ID vrednosti
     * @param observabilityProperties konfiguraciona svojstva biblioteke
     * @param userIdMdcService servis za rad sa user ID vrednoscu u MDC-u
     * @return filter za correlation ID obradu
     */
    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter(
            CorrelationIdService correlationIdService,
            ObservabilityProperties observabilityProperties,
            UserIdMdcService userIdMdcService
    ) {
        return new CorrelationIdFilter(correlationIdService, observabilityProperties, userIdMdcService);
    }


    /**
     * Registruje servlet filter registration za {@link CorrelationIdFilter}.
     * <p>
     * Filter se izvrsava za sve URL putanje i ima najvisi prioritet u lancu filtera.
     *
     * @param correlationIdFilter filter za correlation ID obradu
     * @return registracija correlation ID filtera
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            CorrelationIdFilter correlationIdFilter
    ) {
        FilterRegistrationBean<CorrelationIdFilter> registrationBean =
                new FilterRegistrationBean<>(correlationIdFilter);

        registrationBean.setName("correlationIdFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }

    /**
     * Registruje servis za logovanje osnovnih informacija o HTTP zahtevima.
     *
     * @return servis za request logging
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingService requestLoggingService() {
        return new RequestLoggingService();
    }

    /**
     * Registruje servis za maskiranje osetljivih podataka pre logovanja.
     *
     * @return servis za maskiranje osetljivih podataka
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMaskingService sensitiveDataMaskingService() {
        return new SensitiveDataMaskingService();
    }

    /**
     * Registruje filter za logovanje HTTP zahteva.
     * <p>
     * Filter meri trajanje obrade zahteva i loguje osnovne informacije
     * o zahtevu i odgovoru uz maskiranje osetljivih podataka.
     *
     * @param requestLoggingService servis za request logging
     * @param maskingService servis za maskiranje osetljivih podataka
     * @return filter za logovanje HTTP zahteva
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpRequestLoggingFilter httpRequestLoggingFilter(
            RequestLoggingService requestLoggingService, SensitiveDataMaskingService maskingService
    ) {
        return new HttpRequestLoggingFilter(requestLoggingService, maskingService);
    }

    /**
     * Registruje servlet filter registration za {@link HttpRequestLoggingFilter}.
     * <p>
     * Filter se izvrsava za sve URL putanje odmah nakon correlation ID filtera.
     *
     * @param httpRequestLoggingFilter filter za logovanje HTTP zahteva
     * @return registracija request logging filtera
     */
    @Bean
    public FilterRegistrationBean<HttpRequestLoggingFilter> httpRequestLoggingFilterRegistration(
            HttpRequestLoggingFilter httpRequestLoggingFilter
    ) {
        FilterRegistrationBean<HttpRequestLoggingFilter> registrationBean =
                new FilterRegistrationBean<>(httpRequestLoggingFilter);

        registrationBean.setName("httpRequestLoggingFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);

        return registrationBean;
    }

    /**
     * Registruje servis za logovanje neobradjenih izuzetaka.
     *
     * @param maskingService servis za maskiranje osetljivih podataka
     * @return servis za logovanje neobradjenih izuzetaka
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionLoggingService exceptionLoggingService(SensitiveDataMaskingService maskingService) {
        return new ExceptionLoggingService(maskingService);
    }

    /**
     * Registruje globalni exception handler za neocekivane greske u aplikaciji.
     *
     * @param exceptionLoggingService servis za logovanje neobradjenih izuzetaka
     * @return globalni exception handler
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(
            ExceptionLoggingService exceptionLoggingService
    ) {
        return new GlobalExceptionHandler(exceptionLoggingService);
    }

    /**
     * Dodatna konfiguracija koja registruje podrazumevani {@link UserIdExtractor}
     * kada je Spring Security dostupan na classpath-u.
     * <p>
     * Ako aplikacija nije vec obezbedila sopstveni {@link UserIdExtractor},
     * koristi se implementacija koja pokusava da izdvoji user ID iz Spring
     * Security autentikacionog konteksta.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.Authentication")
    static class SecurityConfig {

        /**
         * Registruje podrazumevani {@link UserIdExtractor} zasnovan na
         * Spring Security autentikacionom kontekstu.
         *
         * @return extractor koji pokusava da izdvoji user ID iz autentikacije
         */
        @Bean
        @ConditionalOnMissingBean(UserIdExtractor.class)
        @ConditionalOnClass(name = "org.springframework.security.core.Authentication")
        public UserIdExtractor jwtAuthenticationUserIdExtractor() {
            return new JwtAuthenticationUserIdExtractor();
        }
    }
}

