package com.banka1.card_service.dto.card_management.response;

import com.banka1.card_service.domain.Card;
import com.banka1.card_service.util.SensitiveDataMasker;
import lombok.Getter;

/**
 * Compact card representation used in list responses.
 * The card number is masked to protect sensitive data — only the first four
 * and last four digits are visible, with asterisks replacing the middle digits.
 *
 * Example:
 * a card number {@code 5798123456785571} is returned as {@code 5798********5571}.
 *
 * The CVV is never included in any list or detail response.
 */
@Getter
public class CardSummaryDTO {

    /**
     * Masked card number safe for display in lists.
     * Format: first 4 digits + 8 asterisks + last 4 digits.
     */
    private final String maskedCardNumber;

    private final String accountNumber;

    public CardSummaryDTO(Card card) {
        this.maskedCardNumber = SensitiveDataMasker.maskCardNumber(card.getCardNumber());
        this.accountNumber = card.getAccountNumber();
    }
}
