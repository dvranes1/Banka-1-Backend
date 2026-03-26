package com.banka1.card_service.dto.card_creation.request;

import com.banka1.card_service.domain.enums.CardBrand;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Personal-account card request payload.
 * The same DTO supports both verification initiation and verification completion.
 *
 * TODO: this needs to be adapted to the original ACCOUNT-SERVICE request DTO
 */
@Getter
@Setter
public class ClientCardRequestDto {

    private String accountNumber;

    private CardBrand cardBrand;

    private BigDecimal cardLimit;

    private Long verificationRequestId;

    @Pattern(regexp = "^\\d{6}$", message = "Verification code must contain exactly 6 digits.")
    private String verificationCode;
}
