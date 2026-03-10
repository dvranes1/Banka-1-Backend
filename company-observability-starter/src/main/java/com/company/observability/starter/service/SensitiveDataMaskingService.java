package com.company.observability.starter.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Servis za maskiranje osetljivih podataka pre logovanja.
 * <p>
 * Podrzava maskiranje osetljivih query parametara i
 * Authorization HTTP zaglavlja.
 */
public class SensitiveDataMaskingService {
    private static final List<Pattern> SENSITIVE_QUERY_PATTERNS = List.of(
            Pattern.compile("(?i)(^|&)(api[_-]?key=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(secret=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(access[_-]?token=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(refresh[_-]?token=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(client[_-]?secret=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(password=)([^&\\s]*)"),
            Pattern.compile("(?i)(^|&)(token=)([^&\\s]*)")
    );

    /**
     * Maskira osetljive vrednosti iz query string-a.
     * <p>
     * Ako query string ne postoji ili je prazan, vraca {@code "-"}.
     *
     * @param query query string HTTP zahteva
     * @return maskirani query string pogodan za logovanje
     */
    public String maskQuery(String query) {
        if (query == null || query.isBlank()) {
            return "-";
        }
        String masked = query;
        for (Pattern p : SENSITIVE_QUERY_PATTERNS) {
            masked = p.matcher(masked).replaceAll("$1$2***");
        }

        return masked;
    }

    /**
     * Maskira vrednost Authorization zaglavlja.
     * <p>
     * Ako zaglavlje pocinje sa {@code Bearer }, vraca {@code Bearer ***}.
     * Za ostale neprazne vrednosti vraca {@code ***}.
     *
     * @param authorizationHeader vrednost Authorization zaglavlja
     * @return maskirana vrednost pogodna za logovanje
     */
    public String maskAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return "-";
        }

        if (authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "Bearer ***";
        }

        return "***";
    }
}
