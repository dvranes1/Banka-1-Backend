package com.company.observability.starter.web;

import com.company.observability.starter.service.ExceptionLoggingService;
import com.company.observability.starter.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * Globalni exception handler za neocekivane greske u aplikaciji.
 * <p>
 * Handler loguje neobradjeni izuzetak zajedno sa osnovnim podacima o HTTP zahtevu
 * i vraca standardizovan JSON odgovor sa informacijama o gresci i correlation ID vrednoscu.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ExceptionLoggingService exceptionLoggingService;

    /**
     * Kreira globalni exception handler.
     *
     * @param exceptionLoggingService servis za logovanje neobradjenih izuzetaka
     */
    public GlobalExceptionHandler(ExceptionLoggingService exceptionLoggingService) {
        this.exceptionLoggingService = exceptionLoggingService;
    }


    /**
     * Obradjuje sve neocekivane izuzetke koji nisu posebno presretnuti drugim handler-ima.
     *
     * @param e izuzetak koji je nastao tokom obrade zahteva
     * @param request HTTP zahtev u kome je greska nastala
     * @return standardizovan odgovor sa podacima o internoj serverskoj gresci
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, HttpServletRequest request) {
        exceptionLoggingService.logUnhandledException(e, request);
        ErrorResponse response = new ErrorResponse(OffsetDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "Unexpected server error", MDC.get(CorrelationIdFilter.MDC_KEY), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}
