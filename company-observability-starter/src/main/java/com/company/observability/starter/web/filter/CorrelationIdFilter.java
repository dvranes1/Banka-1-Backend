package com.company.observability.starter.web.filter;

import com.company.observability.starter.config.ObservabilityProperties;
import com.company.observability.starter.domain.CorrelationContext;
import com.company.observability.starter.service.CorrelationIdService;
import com.company.observability.starter.service.UserIdMdcService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Servlet filter zaduzen za obradu correlation ID vrednosti.
 * <p>
 * Filter cita correlation ID iz dolaznog HTTP zaglavlja, ili generise novu vrednost
 * ako zaglavlje nije prisutno. Zatim upisuje correlation ID u MDC, prosledjuje ga
 * kroz response header i po zavrsetku zahteva cisti MDC kontekst.
 * <p>
 * Ako je ukljucena user ID MDC funkcionalnost, filter upisuje i user ID u MDC
 * za vreme trajanja obrade zahteva.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    private final CorrelationIdService correlationIdService;
    private final ObservabilityProperties observabilityProperties;
    private final UserIdMdcService userIdMdcService;

    /**
     * Kreira filter za obradu correlation ID vrednosti.
     *
     * @param correlationIdService servis za razresavanje correlation ID vrednosti
     * @param observabilityProperties konfiguraciona svojstva biblioteke
     * @param userIdMdcService servis za rad sa user ID vrednoscu u MDC kontekstu
     */
    public CorrelationIdFilter(CorrelationIdService correlationIdService, ObservabilityProperties observabilityProperties, UserIdMdcService userIdMdcService) {
        this.correlationIdService = correlationIdService;
        this.observabilityProperties = observabilityProperties;
        this.userIdMdcService = userIdMdcService;
    }

    /**
     * Obradjuje correlation ID za tekuci HTTP zahtev.
     * <p>
     * Correlation ID se cita iz konfigurisanog zaglavlja ili se generise nova vrednost.
     * Nakon toga se vrednost upisuje u MDC i vraca kroz response header.
     *
     * @param request dolazni HTTP zahtev
     * @param response odlazni HTTP odgovor
     * @param filterChain lanac filtera kroz koji zahtev nastavlja obradu
     * @throws ServletException ako dodje do servlet greske tokom obrade
     * @throws IOException ako dodje do I/O greske tokom obrade
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Map<String, String> previousMdcContext = MDC.getCopyOfContextMap();

        String headerName = observabilityProperties.getCorrelationHeaderName();
        String incomingCID = request.getHeader(headerName);
        CorrelationContext correlationContext = correlationIdService.resolve(incomingCID);

        MDC.put(MDC_KEY, correlationContext.correlationId());
        userIdMdcService.putUserIdIfPresent();
        response.setHeader(headerName, correlationContext.correlationId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            if(previousMdcContext == null || previousMdcContext.isEmpty()){
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdcContext);
            }
        }
    }
}
