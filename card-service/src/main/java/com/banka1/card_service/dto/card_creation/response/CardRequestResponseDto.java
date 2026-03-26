package com.banka1.card_service.dto.card_creation.response;

/**
 * Response for client-initiated card request flows.
 *
 * @param status current flow status
 * @param message human-readable result message
 * @param verificationRequestId pending verification ID when the flow is not yet completed
 * @param createdCard created card payload for completed requests
 */
public record CardRequestResponseDto(
        String status,
        String message,
        Long verificationRequestId,
        CardCreationResponseDto createdCard
) {
}
