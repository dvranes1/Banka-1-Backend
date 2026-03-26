package com.banka1.card_service.dto.card_creation.request;

import com.banka1.card_service.domain.enums.CardBrand;
import com.banka1.card_service.domain.enums.CardRequestRecipientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Business-account card request payload.
 * The same DTO supports both verification initiation and verification completion.
 */
@Getter
@Setter
public class BusinessCardRequestDto {

    private String accountNumber;

    private CardRequestRecipientType recipientType;

    private Long authorizedPersonId;

    @Valid
    private AuthorizedPersonRequestDto authorizedPerson;

    private CardBrand cardBrand;

    private BigDecimal cardLimit;

    private Long verificationRequestId;

    @Pattern(regexp = "^\\d{6}$", message = "Verification code must contain exactly 6 digits.")
    private String verificationCode;
}
