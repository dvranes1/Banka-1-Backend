package com.banka1.card_service.service;

import com.banka1.card_service.dto.card_creation.request.AutoCardCreationRequestDto;
import com.banka1.card_service.dto.card_creation.request.BusinessCardRequestDto;
import com.banka1.card_service.dto.card_creation.request.ClientCardRequestDto;
import com.banka1.card_service.dto.card_creation.response.CardCreationResponseDto;
import com.banka1.card_service.dto.card_creation.response.CardRequestResponseDto;

/**
 * Orchestrates automatic and client-initiated card creation flows.
 */
public interface CardRequestService {

    /**
     * Creates a card immediately for an internal automatic flow.
     *
     * @param request internal creation request
     * @return created card payload
     */
    CardCreationResponseDto createAutomaticCard(AutoCardCreationRequestDto request);

    /**
     * Starts or completes a personal-account card request.
     *
     * @param authenticatedClientId authenticated client ID
     * @param request request payload
     * @return pending or completed flow response
     */
    CardRequestResponseDto processManualCardRequest(Long authenticatedClientId, ClientCardRequestDto request);

    /**
     * Starts or completes a business-account card request.
     *
     * @param authenticatedClientId authenticated client ID
     * @param request request payload
     * @return pending or completed flow response
     */
    CardRequestResponseDto processBusinessCardRequest(Long authenticatedClientId, BusinessCardRequestDto request);

}
