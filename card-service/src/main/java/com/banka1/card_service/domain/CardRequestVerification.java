package com.banka1.card_service.domain;

import com.banka1.card_service.domain.enums.AccountOwnershipType;
import com.banka1.card_service.domain.enums.AuthorizedPersonGender;
import com.banka1.card_service.domain.enums.CardBrand;
import com.banka1.card_service.domain.enums.CardRequestRecipientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pending card request that waits for a verification code before card creation.
 */
@Entity
@Table(
        name = "card_request_verifications",
        indexes = {
                @Index(name = "idx_card_req_ver_client_id", columnList = "client_id"),
                @Index(name = "idx_card_req_ver_account_number", columnList = "account_number"),
                @Index(name = "idx_card_req_ver_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CardRequestVerification extends BaseEntity {

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false, length = 20)
    private AccountOwnershipType ownershipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 30)
    private CardRequestRecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand", nullable = false, length = 20)
    private CardBrand cardBrand;

    @Column(name = "card_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal cardLimit;

    @Column(name = "verification_code_hash", nullable = false, length = 64)
    private String verificationCodeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "authorized_person_id")
    private Long authorizedPersonId;

    @Column(name = "authorized_first_name", length = 100)
    private String authorizedFirstName;

    @Column(name = "authorized_last_name", length = 100)
    private String authorizedLastName;

    @Column(name = "authorized_date_of_birth")
    private LocalDate authorizedDateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorized_gender", length = 20)
    private AuthorizedPersonGender authorizedGender;

    @Column(name = "authorized_email", length = 255)
    private String authorizedEmail;

    @Column(name = "authorized_phone", length = 50)
    private String authorizedPhone;

    @Column(name = "authorized_address", length = 255)
    private String authorizedAddress;
}
