package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO za jednostranu (one-sided) operaciju nad jednim racunom.
 * <p>
 * Koristi se kada sredstva treba samo skinuti ili dodati na racun bez parnjaka
 * (kontra-strane). Tipican slucaj je settlement berzanske kupovine/prodaje gde
 * po direktivi PM-a (GHI #199) <strong>iznos trgovine ne sme da prolazi kroz
 * bankin racun</strong>: pri BUY-u se sredstva samo skinu sa korisnikovog
 * racuna, pri SELL-u se samo upiseu. Provizija (commission) se i dalje prebacuje
 * na bankin racun klasicnim {@code transaction}/{@code transfer} putem.
 * <p>
 * Validacija:
 * <ul>
 *   <li>{@code accountNumber} mora biti 19-cifren (kao i svuda u sistemu)</li>
 *   <li>{@code amount} mora biti pozitivan</li>
 *   <li>{@code clientId} obavezan zarad audit log-a</li>
 * </ul>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OneSidedTransactionDto {
    /** Broj racuna nad kojim se izvodi operacija. */
    @NotBlank(message = "Unesi broj racuna")
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String accountNumber;

    /** Iznos koji se skida (debit) ili dodaje (credit). U valuti racuna. */
    @NotNull(message = "Unesi iznos")
    @DecimalMin(value = "0.0", inclusive = false, message = "Iznos mora biti veci od 0")
    private BigDecimal amount;

    /**
     * ID klijenta-vlasnika racuna. Sluzi za audit log i verifikaciju da pozivajuca
     * strana zaista obraduje operaciju za pravog vlasnika racuna.
     */
    @NotNull(message = "Unesi id klijenta")
    private Long clientId;

    /** Slobodan tekstualni opis operacije za audit. */
    private String description;
}
