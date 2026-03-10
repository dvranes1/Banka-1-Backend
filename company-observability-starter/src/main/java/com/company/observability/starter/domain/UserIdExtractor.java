package com.company.observability.starter.domain;

import java.util.Optional;

/**
 * Interfejs za izdvajanje user ID vrednosti iz trenutnog bezbednosnog ili
 * aplikacionog konteksta.
 * <p>
 * Koristi se za opcionalni upis user ID vrednosti u MDC kako bi logovi
 * sadrzali informaciju o korisniku povezanom sa trenutnim zahtevom.
 */
public interface UserIdExtractor {

    /**
     * Pokusava da izdvoji user ID vrednost iz trenutnog konteksta.
     *
     * @return {@link java.util.Optional} sa user ID vrednoscu ako je dostupna,
     *         inace prazan {@link java.util.Optional}
     */
    Optional<String> extractUserId();
}