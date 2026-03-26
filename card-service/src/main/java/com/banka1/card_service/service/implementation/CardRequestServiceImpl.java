package com.banka1.card_service.service.implementation;

import com.banka1.card_service.domain.AuthorizedPerson;
import com.banka1.card_service.domain.Card;
import com.banka1.card_service.domain.CardRequestVerification;
import com.banka1.card_service.domain.enums.AccountOwnershipType;
import com.banka1.card_service.domain.enums.CardBrand;
import com.banka1.card_service.domain.enums.CardRequestRecipientType;
import com.banka1.card_service.domain.enums.CardStatus;
import com.banka1.card_service.dto.card_creation.internal.CardCreationResult;
import com.banka1.card_service.dto.card_creation.internal.CreateCardCommand;
import com.banka1.card_service.dto.card_creation.request.AuthorizedPersonRequestDto;
import com.banka1.card_service.dto.card_creation.request.AutoCardCreationRequestDto;
import com.banka1.card_service.dto.card_creation.request.BusinessCardRequestDto;
import com.banka1.card_service.dto.card_creation.request.ClientCardRequestDto;
import com.banka1.card_service.dto.card_creation.response.CardCreationResponseDto;
import com.banka1.card_service.dto.card_creation.response.CardRequestResponseDto;
import com.banka1.card_service.dto.card_management.internal.CardNotificationDto;
import com.banka1.card_service.dto.enums.CardNotificationType;
import com.banka1.card_service.exception.BusinessException;
import com.banka1.card_service.exception.ErrorCode;
import com.banka1.card_service.mapper.CardCreationResponseMapper;
import com.banka1.card_service.rabbitMQ.RabbitClient;
import com.banka1.card_service.repository.AuthorizedPersonRepository;
import com.banka1.card_service.repository.CardRepository;
import com.banka1.card_service.repository.CardRequestVerificationRepository;
import com.banka1.card_service.rest_client.AccountNotificationContextDto;
import com.banka1.card_service.rest_client.AccountService;
import com.banka1.card_service.rest_client.ClientNotificationRecipientDto;
import com.banka1.card_service.rest_client.ClientService;
import com.banka1.card_service.service.CardCreationService;
import com.banka1.card_service.service.CardRequestService;
import com.banka1.card_service.service.CardVerificationCodeService;
import com.banka1.card_service.util.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default implementation of card-request flows with email verification.
 */
@Service
@RequiredArgsConstructor
public class CardRequestServiceImpl implements CardRequestService {

    private static final CardBrand[] AUTOMATIC_CARD_BRANDS = CardBrand.values();

    private final CardCreationService cardCreationService;
    private final CardRepository cardRepository;
    private final CardRequestVerificationRepository cardRequestVerificationRepository;
    private final AuthorizedPersonRepository authorizedPersonRepository;
    private final AccountService accountService;
    private final ClientService clientService;
    private final CardVerificationCodeService cardVerificationCodeService;
    private final RabbitClient rabbitClient;
    private final CardCreationResponseMapper cardCreationResponseMapper;

    @Value("${card.request.verification.expiration-minutes}")
    private long verificationExpirationMinutes;

    @Value("${card.creation.automatic.default-limit}")
    private BigDecimal automaticCardDefaultLimit;

    @Override
    @Transactional
    public CardCreationResponseDto createAutomaticCard(AutoCardCreationRequestDto request) {
        requireText(request.getAccountNumber(), ErrorCode.INVALID_ACCOUNT_NUMBER, "Account number must not be blank.");
        if (request.getClientId() == null) {
            throw new BusinessException(ErrorCode.INVALID_CLIENT_ID, "Client ID must be provided.");
        }

        CardCreationResult result = cardCreationService.createCard(new CreateCardCommand(
                request.getAccountNumber(),
                randomAutomaticCardBrand(),
                automaticCardDefaultLimit,
                request.getClientId(),
                null
        ));
        return cardCreationResponseMapper.toDto(result);
    }

    /**
     * Entry point for the manual personal-card flow.
     * This method intentionally handles both phases of the same workflow through one endpoint:

     * 1. initiation:
     * caller sends card data only, without verification fields
     * Example request body:
     * {@code {"accountNumber":"265000000000123456","cardBrand":"VISA","cardLimit":50000}}
     *
     * Result:
     * the method stores a pending {@link CardRequestVerification}, sends a verification email,
     * and returns a response such as:
     * {@code {"status":"PENDING_VERIFICATION","verificationRequestId":42}}
     *
     * 2. completion:
     * caller sends only the verification identifiers
     * Example request body:
     * {@code {"verificationRequestId":42,"verificationCode":"123456"}}
     *
     * Result:
     * the method validates the stored verification request, creates the card,
     * sends a success notification, and returns a response such as:
     * {@code {"status":"COMPLETED","createdCard":{...}}}
     *
     * Flow selector rules:
     * - if both {@code verificationRequestId} and {@code verificationCode} are absent,
     *   this is treated as initiation and {@link #initiatePersonalRequest(Long, ClientCardRequestDto)} is called
     * - if both fields are present,
     *   this is treated as completion and {@link #completePersonalRequest(Long, ClientCardRequestDto)} is called
     * - if only one of the two fields is present,
     *   the request is invalid and {@link #verifyFlowSelector(Long, String)} throws

     * Important:
     * the per-account personal-card limit is checked twice:
     * once before a verification email is sent, and again immediately before card creation.
     * This prevents a race where the client was eligible during initiation but exceeds the limit later.
     *
     * @param authenticatedClientId ID extracted from the authenticated JWT
     * @param request personal-card request DTO used for either initiation or completion
     * @return {@code PENDING_VERIFICATION} when the flow is started, or {@code COMPLETED} when the card is created
     */
    @Override
    @Transactional
    public CardRequestResponseDto processManualCardRequest(Long authenticatedClientId, ClientCardRequestDto request) {
        verifyFlowSelector(request.getVerificationRequestId(), request.getVerificationCode());
        if (isVerificationCompletion(request.getVerificationRequestId(), request.getVerificationCode())) {
            return completePersonalRequest(authenticatedClientId, request);
        }
        return initiatePersonalRequest(authenticatedClientId, request);
    }

    /**
     * Entry point for the manual business-card flow.
     * This method handles both phases of the same business workflow through one endpoint:

     * 1. initiation:
     * caller sends business-card data and recipient information, without verification fields
     * Example request body for the owner:
     * {@code {"accountNumber":"265000000000999999","cardBrand":"VISA","cardLimit":50000,"recipientType":"OWNER"}}
     *
     * Example request body for an authorized person:
     * {@code {"accountNumber":"265000000000999999","cardBrand":"VISA","cardLimit":50000,"recipientType":"AUTHORIZED_PERSON","authorizedPerson":{...}}}
     *
     * Result:
     * the method stores a pending {@link CardRequestVerification}, sends a verification email to the owner,
     * and returns a response such as:
     * {@code {"status":"PENDING_VERIFICATION","verificationRequestId":91}}
     *
     * 2. completion:
     * caller sends only the verification identifiers
     * Example request body:
     * {@code {"verificationRequestId":91,"verificationCode":"123456"}}
     *
     * Result:
     * the method validates the stored verification request, creates the business card,
     * optionally links it to an authorized person, sends success notifications,
     * and returns a response such as:
     * {@code {"status":"COMPLETED","createdCard":{...}}}
     *
     * Flow selector rules:
     * - if both {@code verificationRequestId} and {@code verificationCode} are absent,
     *   this is treated as initiation and {@link #initiateBusinessRequest(Long, BusinessCardRequestDto)} is called
     * - if both fields are present,
     *   this is treated as completion and {@link #completeBusinessRequest(Long, BusinessCardRequestDto)} is called
     * - if only one of the two fields is present,
     *   the request is invalid and {@link #verifyFlowSelector(Long, String)} throws

     * Important:
     * the business-card limit is checked twice:
     * once before the verification email is sent, and again immediately before card creation.
     * The rule is 1 active business card per person on the same account.
     *
     * @param authenticatedClientId ID extracted from the authenticated JWT of the business owner
     * @param request business-card request DTO used for either initiation or completion
     * @return {@code PENDING_VERIFICATION} when the flow is started, or {@code COMPLETED} when the card is created
     */
    @Override
    @Transactional
    public CardRequestResponseDto processBusinessCardRequest(Long authenticatedClientId, BusinessCardRequestDto request) {
        verifyFlowSelector(request.getVerificationRequestId(), request.getVerificationCode());
        if (isVerificationCompletion(request.getVerificationRequestId(), request.getVerificationCode())) {
            return completeBusinessRequest(authenticatedClientId, request);
        }
        return initiateBusinessRequest(authenticatedClientId, request);
    }

    /**
     * Starts the personal-card request flow.
     * This phase DOES NOT CREATE a card YET.

     * What this method does:
     * - validates that 1. account number, 2. brand, and 3. limit are present and valid (validateInitiationRequest)
     * - loads the account context and verifies that the authenticated client owns the account
     * - rejects business accounts, because they must use the business endpoint
     * - checks that the client does not already have 2 active personal cards for the same account
     * - generates a verification code
     * - stores all creation inputs in {@link CardRequestVerification}
     * - schedules a verification email after the transaction commits

     * Example input:
     * {@code {"accountNumber":"265000000000123456","cardBrand":"VISA","cardLimit":50000}}
     *
     * Example output:
     * {@code {"status":"PENDING_VERIFICATION","message":"Verification code sent.","verificationRequestId":42}}
     *
     * @param authenticatedClientId ID of the authenticated client
     * @param request initiation payload with card creation data
     * @return pending-verification response containing the stored verification request ID
     */
    private CardRequestResponseDto initiatePersonalRequest(Long authenticatedClientId, ClientCardRequestDto request) {
        validateInitiationRequest(request.getAccountNumber(), request.getCardBrand(), request.getCardLimit());

        AccountNotificationContextDto accountContext = accountService.getAccountContext(request.getAccountNumber());

        assertOwner(authenticatedClientId, accountContext.ownerClientId());
        if (accountContext.isBusinessAccount()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ACCOUNT_TYPE,
                    "Business accounts must use the /request/business endpoint."
            );
        }

        enforcePersonalLimitOf2Accounts(request.getAccountNumber(), authenticatedClientId);

        String verificationCode = cardVerificationCodeService.generateCode();
        CardRequestVerification verification = new CardRequestVerification();
        verification.setAccountNumber(request.getAccountNumber().strip());
        verification.setClientId(authenticatedClientId);
        verification.setOwnershipType(AccountOwnershipType.PERSONAL);
        verification.setRecipientType(CardRequestRecipientType.OWNER);
        verification.setCardBrand(request.getCardBrand());
        verification.setCardLimit(request.getCardLimit());  // TODO: mozda ovo treba malo bolje kontrolisati?
        verification.setVerificationCodeHash(cardVerificationCodeService.sha256Hex(verificationCode));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(verificationExpirationMinutes));
        verification.setConsumed(false);
        verification = cardRequestVerificationRepository.save(verification);

        ClientNotificationRecipientDto ownerRecipient = clientService.getNotificationRecipient(authenticatedClientId);
        registerAfterCommitVerificationEmail(ownerRecipient, verification, verificationCode);

        return new CardRequestResponseDto(
                "PENDING_VERIFICATION",
                "Verification code sent.",
                verification.getId(),
                null
        );
    }

    /**
     * Completes the personal-card request flow after the client enters the email verification code.

     * What this method does:
     * - loads the stored verification request by ID
     * - verifies that the request belongs to the authenticated client
     * - validates ownership type, recipient type, expiration, consumed state, and verification code
     * - checks the 2-cards-per-account personal limit again
     * - creates the card from the values previously stored in {@link CardRequestVerification}
     * - marks the verification request as consumed
     * - schedules a success email after commit
     * - converts unexpected technical failures into a structured {@link BusinessException}
     *
     * Example input:
     * {@code {"verificationRequestId":42,"verificationCode":"123456"}}
     *
     * Example output:
     * {@code {"status":"COMPLETED","message":"Card created successfully.","createdCard":{...}}}
     *
     * @param authenticatedClientId ID of the authenticated client
     * @param request completion payload with verification request ID and verification code
     * @return completed response with the newly created card
     */
    private CardRequestResponseDto completePersonalRequest(Long authenticatedClientId, ClientCardRequestDto request) {
        CardRequestVerification verification = findOwnedVerificationRequest(
                authenticatedClientId,
                request.getVerificationRequestId()
        );
        validateVerificationRequest(
                verification,
                request.getVerificationCode(),
                AccountOwnershipType.PERSONAL,
                CardRequestRecipientType.OWNER
        );

        try {
            enforcePersonalLimitOf2Accounts(verification.getAccountNumber(), verification.getClientId());
            CardCreationResult result = cardCreationService.createCard(new CreateCardCommand(
                    verification.getAccountNumber(),
                    verification.getCardBrand(),
                    verification.getCardLimit(),
                    verification.getClientId(),
                    null
            ));
            verification.setConsumed(true);
            cardRequestVerificationRepository.save(verification);

            ClientNotificationRecipientDto ownerRecipient = clientService.getNotificationRecipient(verification.getClientId());
            registerAfterCommitRequestSuccess(ownerRecipient, null, result.card());

            return new CardRequestResponseDto(
                    "COMPLETED",
                    "Card created successfully.",
                    null,
                    cardCreationResponseMapper.toDto(result)
            );
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.CARD_REQUEST_COMPLETION_FAILED,
                    "Unable to complete personal card request."
            );
        }
    }

    /**
     * Starts the business-card request flow.
     * This phase does not create a card yet.

     * What this method does:
     * - validates account number, brand, limit, and recipient type
     * - verifies that the linked account is a business account
     * - verifies that the authenticated client owns the business account
     * - resolves the target person for the card:
     *   either the owner themselves, or an existing/new authorized person
     * - checks the business rule that each person may have at most 1 active card per account
     * - generates a verification code
     * - stores all card-creation inputs in {@link CardRequestVerification}
     * - stores an authorized-person snapshot when the request targets an authorized person
     * - schedules a verification email to the owner after commit

     * Example owner input:
     * {@code {"accountNumber":"265000000000999999","cardBrand":"VISA","cardLimit":50000,"recipientType":"OWNER"}}
     *
     * Example authorized-person input:
     * {@code {"accountNumber":"265000000000999999","cardBrand":"MASTERCARD","cardLimit":50000,"recipientType":"AUTHORIZED_PERSON","authorizedPerson":{...}}}
     *
     * Example output:
     * {@code {"status":"PENDING_VERIFICATION","message":"Verification code sent.","verificationRequestId":91}}
     *
     * @param authenticatedClientId ID of the authenticated business owner
     * @param request initiation payload with business-card data
     * @return pending-verification response containing the stored verification request ID
     */
    private CardRequestResponseDto initiateBusinessRequest(Long authenticatedClientId, BusinessCardRequestDto request) {
        validateInitiationRequest(request.getAccountNumber(), request.getCardBrand(), request.getCardLimit());
        if (request.getRecipientType() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "Recipient type must be provided.");
        }

        AccountNotificationContextDto accountContext = accountService.getAccountContext(request.getAccountNumber());
        if (!accountContext.isBusinessAccount()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ACCOUNT_TYPE,
                    "Personal accounts must use the /request endpoint."
            );
        }
        assertOwner(authenticatedClientId, accountContext.ownerClientId());

        AuthorizedPerson resolvedAuthorizedPerson = null;
        AuthorizedPersonRequestDto authorizedPersonRequest = null;
        if (request.getRecipientType() == CardRequestRecipientType.AUTHORIZED_PERSON) {
            resolvedAuthorizedPerson = resolveExistingAuthorizedPerson(request);
            authorizedPersonRequest = request.getAuthorizedPerson();
            Long authorizedPersonId = resolvedAuthorizedPerson == null ? null : resolvedAuthorizedPerson.getId();
            enforceBusinessLimit(request.getAccountNumber(), authenticatedClientId, authorizedPersonId);
        } else {
            enforceBusinessLimit(request.getAccountNumber(), authenticatedClientId, null);
        }

        String verificationCode = cardVerificationCodeService.generateCode();
        CardRequestVerification verification = new CardRequestVerification();
        verification.setAccountNumber(request.getAccountNumber().strip());
        verification.setClientId(authenticatedClientId);
        verification.setOwnershipType(AccountOwnershipType.BUSINESS);
        verification.setRecipientType(request.getRecipientType());
        verification.setCardBrand(request.getCardBrand());
        verification.setCardLimit(request.getCardLimit());
        verification.setVerificationCodeHash(cardVerificationCodeService.sha256Hex(verificationCode));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(verificationExpirationMinutes));
        verification.setConsumed(false);
        if (resolvedAuthorizedPerson != null) {
            verification.setAuthorizedPersonId(resolvedAuthorizedPerson.getId());
            copyAuthorizedPersonSnapshot(verification, resolvedAuthorizedPerson);
        } else if (authorizedPersonRequest != null) {
            copyAuthorizedPersonSnapshot(verification, authorizedPersonRequest);
        }
        verification = cardRequestVerificationRepository.save(verification);

        ClientNotificationRecipientDto ownerRecipient = clientService.getNotificationRecipient(authenticatedClientId);
        registerAfterCommitVerificationEmail(ownerRecipient, verification, verificationCode);

        return new CardRequestResponseDto(
                "PENDING_VERIFICATION",
                "Verification code sent.",
                verification.getId(),
                null
        );
    }

    /**
     * Completes the business-card request flow after the owner enters the email verification code.

     * What this method does:
     * - loads the stored verification request by ID
     * - verifies that the request belongs to the authenticated owner
     * - validates ownership type, recipient type, expiration, consumed state, and verification code
     * - resolves or creates the target authorized person when needed
     * - checks the 1-card-per-person business rule again
     * - creates the card from the values previously stored in {@link CardRequestVerification}
     * - links the new card ID to the authorized person when the card was issued for them
     * - marks the verification request as consumed
     * - schedules success notifications after commit
     * - sends an immediate failure notification if completion fails after the request already exists

     * Example input:
     * {@code {"verificationRequestId":91,"verificationCode":"123456"}}
     *
     * Example output:
     * {@code {"status":"COMPLETED","message":"Card created successfully.","createdCard":{...}}}
     *
     * @param authenticatedClientId ID of the authenticated business owner
     * @param request completion payload with verification request ID and verification code
     * @return completed response with the newly created card
     */
    private CardRequestResponseDto completeBusinessRequest(Long authenticatedClientId, BusinessCardRequestDto request) {
        CardRequestVerification verification = findOwnedVerificationRequest(
                authenticatedClientId,
                request.getVerificationRequestId()
        );
        validateVerificationRequest(
                verification,
                request.getVerificationCode(),
                AccountOwnershipType.BUSINESS,
                verification.getRecipientType()
        );

        try {
            AuthorizedPerson authorizedPerson = null;
            if (verification.getRecipientType() == CardRequestRecipientType.AUTHORIZED_PERSON) {
                authorizedPerson = materializeAuthorizedPerson(verification);
                enforceBusinessLimit(
                        verification.getAccountNumber(),
                        verification.getClientId(),
                        authorizedPerson.getId()
                );
            } else {
                enforceBusinessLimit(verification.getAccountNumber(), verification.getClientId(), null);
            }

            CardCreationResult result = cardCreationService.createCard(new CreateCardCommand(
                    verification.getAccountNumber(),
                    verification.getCardBrand(),
                    verification.getCardLimit(),
                    verification.getClientId(),
                    authorizedPerson == null ? null : authorizedPerson.getId()
            ));

            if (authorizedPerson != null) {
                authorizedPerson.getCardIds().add(result.card().getId());
                authorizedPersonRepository.save(authorizedPerson);
            }

            verification.setConsumed(true);
            cardRequestVerificationRepository.save(verification);

            ClientNotificationRecipientDto ownerRecipient = clientService.getNotificationRecipient(verification.getClientId());
            NotificationRecipient authorizedRecipient = authorizedPerson == null ? null : toNotificationRecipient(authorizedPerson);
            registerAfterCommitRequestSuccess(ownerRecipient, authorizedRecipient, result.card());

            return new CardRequestResponseDto(
                    "COMPLETED",
                    "Card created successfully.",
                    null,
                    cardCreationResponseMapper.toDto(result)
            );
        } catch (RuntimeException ex) {
            NotificationRecipient authorizedRecipient = verification.getRecipientType() == CardRequestRecipientType.AUTHORIZED_PERSON
                    ? toNotificationRecipient(verification)
                    : null;
            sendFailureNotification(verification, authorizedRecipient, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Validates the common initiation payload fields shared by personal and business request flows.
     *
     * <p>The initiation step requires a non-blank account number, an explicit card brand,
     * and a non-negative card limit before any account or ownership checks are performed.
     *
     * @param accountNumber target account number
     * @param cardBrand requested card brand
     * @param cardLimit requested spending limit
     */
    private void validateInitiationRequest(String accountNumber, CardBrand cardBrand, BigDecimal cardLimit) {
        requireText(accountNumber, ErrorCode.INVALID_ACCOUNT_NUMBER, "Account number must not be blank.");
        if (cardBrand == null) {
            throw new BusinessException(ErrorCode.INVALID_CARD_BRAND, "Card brand must be provided.");
        }
        if (cardLimit == null || cardLimit.signum() < 0) {
            throw new BusinessException(ErrorCode.INVALID_CARD_LIMIT, "Card limit must be zero or greater.");
        }
    }

    /**
     * Validates whether the caller selected a legal phase of the two-step manual flow.

     * Valid combinations:
     * - both values are {@code null}: initiation flow
     * - both values are present: completion flow

     * Invalid combinations:
     * - request ID without code
     * - code without request ID

     * Example:
     * {@code verifyFlowSelector(null, null)} is valid
     * {@code verifyFlowSelector(42L, "123456")} is valid
     * {@code verifyFlowSelector(42L, null)} throws
     *
     * @param verificationRequestId stored verification request identifier
     * @param verificationCode user-entered six-digit code
     */
    private void verifyFlowSelector(Long verificationRequestId, String verificationCode) {
        if ((verificationRequestId == null) != (verificationCode == null)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST_STATE,
                    "Verification request ID and verification code must be supplied together."
            );
        }
    }

    /**
     * Determines whether the current request should be treated as verification completion.

     * Returns {@code true} only when both verification fields are present.
     * Returns {@code false} when both are absent, which means the flow should start a new request.

     * Example:
     * - {@code isVerificationCompletion(null, null)} returns {@code false}
     * - {@code isVerificationCompletion(42L, "123456")} returns {@code true}
     *
     * The illegal partial cases are rejected earlier by {@link #verifyFlowSelector(Long, String)}.
     *
     * @param verificationRequestId stored verification request identifier
     * @param verificationCode user-entered verification code
     * @return {@code true} for completion, {@code false} for initiation
     */
    private boolean isVerificationCompletion(Long verificationRequestId, String verificationCode) {
        return verificationRequestId != null && verificationCode != null;
    }

    /**
     * Verifies that the authenticated client is the owner of the referenced account.
     *
     * @param authenticatedClientId client ID extracted from authentication
     * @param ownerClientId expected owner client ID
     */
    private void assertOwner(Long authenticatedClientId, Long ownerClientId) {
        if (ownerClientId == null || !ownerClientId.equals(authenticatedClientId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You do not own this account.");
        }
    }

    /**
     * Enforces the personal-account rule: a client can have at most 2 non-deactivated cards per account.

     * The repository query counts active cards that belong to:
     * - the same account
     * - the same owner client
     * - no authorized person
     * - status other than {@link CardStatus#DEACTIVATED}
     *
     * This check is executed during both initiation and completion.

     * Example:
     * - count = 0 or 1: request may continue
     * - count = 2 or more: throws {@link BusinessException} with {@link ErrorCode#MAX_CARD_LIMIT_REACHED}
     *
     * @param accountNumber linked account number
     * @param clientId owner client ID
     */
    private void enforcePersonalLimitOf2Accounts(String accountNumber, Long clientId) {
        long count = cardRepository.countByAccountNumberAndClientIdAndAuthorizedPersonIdIsNullAndStatusNot(
                accountNumber.strip(),
                clientId,
                CardStatus.DEACTIVATED
        );
        if (count >= 2) {
            throw new BusinessException(
                    ErrorCode.MAX_CARD_LIMIT_REACHED,
                    "Personal accounts can have at most 2 active cards."
            );
        }
    }

    /**
     * Enforces the business-account rule: each person may have at most 1 non-deactivated card per account.

     * The query path depends on the target person:
     * - when {@code authorizedPersonId == null}, the card is for the business owner,
     *   so the count is performed by {@code accountNumber + clientId}
     * - when {@code authorizedPersonId != null}, the card is for an authorized person,
     *   so the count is performed by {@code accountNumber + authorizedPersonId}
     *
     * This check is executed during both initiation and completion.

     * Example:
     * - owner has 0 cards on that account: request may continue
     * - authorized person already has 1 active card on that account: throws
     *
     * @param accountNumber linked business account number
     * @param clientId owner client ID
     * @param authorizedPersonId optional authorized-person ID
     */
    private void enforceBusinessLimit(String accountNumber, Long clientId, Long authorizedPersonId) {
        long count = authorizedPersonId == null
                ? cardRepository.countByAccountNumberAndClientIdAndAuthorizedPersonIdIsNullAndStatusNot(
                        accountNumber.strip(),
                        clientId,
                        CardStatus.DEACTIVATED
                )
                : cardRepository.countByAccountNumberAndAuthorizedPersonIdAndStatusNot(
                        accountNumber.strip(),
                        authorizedPersonId,
                        CardStatus.DEACTIVATED
                );
        if (count >= 1) {
            throw new BusinessException(
                    ErrorCode.MAX_CARD_LIMIT_REACHED,
                    "Business accounts can have at most 1 active card per person."
            );
        }
    }

    /**
     * Loads a stored verification request and ensures that it belongs to the authenticated client.
     *
     * Example:
     * if request ID {@code 42} exists but belongs to another client,
     * the method throws {@link ErrorCode#ACCESS_DENIED}.
     *
     * @param authenticatedClientId ID from the authenticated JWT
     * @param verificationRequestId ID of the stored verification request
     * @return matching verification entity owned by this client
     */
    private CardRequestVerification findOwnedVerificationRequest(Long authenticatedClientId, Long verificationRequestId) {
        CardRequestVerification verification = cardRequestVerificationRepository.findById(verificationRequestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VERIFICATION_REQUEST_NOT_FOUND,
                        "Verification request was not found."
                ));
        if (!verification.getClientId().equals(authenticatedClientId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You do not own this verification request.");
        }
        return verification;
    }

    /**
     * Validates that a stored verification request is still usable for the expected flow.

     * This method checks:
     * - the request was not consumed earlier
     * - the request belongs to the expected ownership flow
     * - the request belongs to the expected recipient type
     * - the request has not expired
     * - the supplied verification code matches the stored hash

     * Example:
     * for the personal-owner flow, the expected values are:
     * {@code expectedOwnershipType = PERSONAL}
     * and
     * {@code expectedRecipientType = OWNER}
     *
     * @param verification stored verification entity
     * @param verificationCode plain verification code entered by the caller
     * @param expectedOwnershipType expected account ownership type for this flow
     * @param expectedRecipientType expected recipient type for this flow
     */
    private void validateVerificationRequest(
            CardRequestVerification verification,
            String verificationCode,
            AccountOwnershipType expectedOwnershipType,
            CardRequestRecipientType expectedRecipientType
    ) {
        if (verification.isConsumed()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST_STATE,
                    "Verification request has already been consumed."
            );
        }
        if (verification.getOwnershipType() != expectedOwnershipType) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_TYPE, "Verification request belongs to another flow.");
        }
        if (verification.getRecipientType() != expectedRecipientType) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "Recipient type does not match verification flow.");
        }
        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    ErrorCode.VERIFICATION_REQUEST_EXPIRED,
                    "Verification request has expired."
            );
        }
        if (!verification.getVerificationCodeHash().equals(cardVerificationCodeService.sha256Hex(verificationCode))) {
            throw new BusinessException(
                    ErrorCode.INVALID_VERIFICATION_CODE,
                    "Verification code is invalid."
            );
        }
    }

    /**
     * Resolves the authorized person referenced by a business request, when the recipient type requires one.

     * Resolution strategy:
     * - if recipient type is not {@code AUTHORIZED_PERSON}, returns {@code null}
     * - if {@code authorizedPersonId} is provided, loads that existing authorized person
     * - otherwise, expects an inline {@code authorizedPerson} payload and tries to find
     *   an existing record by email + first name + last name + date of birth
     * - if no match exists, returns {@code null}, which means a new authorized person will be created later

     * Example:
     * a request may reference an existing person by ID, or provide full identity data for lookup.
     *
     * @param request business-card request payload
     * @return resolved existing authorized person, or {@code null} when a new person must be created later
     */
    private AuthorizedPerson resolveExistingAuthorizedPerson(BusinessCardRequestDto request) {
        if (request.getRecipientType() != CardRequestRecipientType.AUTHORIZED_PERSON) {
            return null;
        }

        if (request.getAuthorizedPersonId() != null) {
            return authorizedPersonRepository.findById(request.getAuthorizedPersonId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AUTHORIZED_PERSON_NOT_FOUND,
                            "Authorized person with ID " + request.getAuthorizedPersonId() + " was not found."
                    ));
        }

        AuthorizedPersonRequestDto authorizedPerson = request.getAuthorizedPerson();
        if (authorizedPerson == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST_STATE,
                    "Authorized-person details must be provided for this request."
            );
        }

        return authorizedPersonRepository.findByEmailIgnoreCaseAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
                authorizedPerson.getEmail().trim(),
                authorizedPerson.getFirstName().trim(),
                authorizedPerson.getLastName().trim(),
                authorizedPerson.getDateOfBirth()
        ).orElse(null);
    }

    /**
     * Materializes the authorized person at business-flow completion time.

     * Behavior:
     * - if the verification request already references an existing authorized-person ID,
     *   this method loads that record
     * - otherwise, it creates a new {@link AuthorizedPerson} from the snapshot stored
     *   inside {@link CardRequestVerification}
     *
     * This delayed materialization keeps the system from creating authorized-person records
     * before the owner successfully confirms the request by verification code.
     *
     * @param verification stored business verification request
     * @return existing or newly created authorized person
     */
    private AuthorizedPerson materializeAuthorizedPerson(CardRequestVerification verification) {
        if (verification.getAuthorizedPersonId() != null) {
            return authorizedPersonRepository.findById(verification.getAuthorizedPersonId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AUTHORIZED_PERSON_NOT_FOUND,
                            "Authorized person with ID " + verification.getAuthorizedPersonId() + " was not found."
                    ));
        }

        AuthorizedPerson authorizedPerson = new AuthorizedPerson();
        authorizedPerson.setFirstName(verification.getAuthorizedFirstName());
        authorizedPerson.setLastName(verification.getAuthorizedLastName());
        authorizedPerson.setDateOfBirth(verification.getAuthorizedDateOfBirth());
        authorizedPerson.setGender(verification.getAuthorizedGender());
        authorizedPerson.setEmail(verification.getAuthorizedEmail());
        authorizedPerson.setPhone(verification.getAuthorizedPhone());
        authorizedPerson.setAddress(verification.getAuthorizedAddress());
        return authorizedPersonRepository.save(authorizedPerson);
    }

    /**
     * Copies a snapshot of an existing authorized person into the verification entity.

     * This snapshot ensures the request keeps the identity data that was approved
     * at initiation time, even if the authorized-person record changes later.
     *
     * @param target verification entity that stores the snapshot
     * @param source existing authorized person
     */
    private void copyAuthorizedPersonSnapshot(CardRequestVerification target, AuthorizedPerson source) {
        target.setAuthorizedFirstName(source.getFirstName());
        target.setAuthorizedLastName(source.getLastName());
        target.setAuthorizedDateOfBirth(source.getDateOfBirth());
        target.setAuthorizedGender(source.getGender());
        target.setAuthorizedEmail(source.getEmail());
        target.setAuthorizedPhone(source.getPhone());
        target.setAuthorizedAddress(source.getAddress());
    }

    /**
     * Copies a snapshot of inline authorized-person request data into the verification entity.

     * This is used when the request does not point to an existing authorized-person record yet.
     * The snapshot is later used by {@link #materializeAuthorizedPerson(CardRequestVerification)}
     * if the owner completes verification successfully.
     *
     * @param target verification entity that stores the snapshot
     * @param source inline authorized-person payload from the request
     */
    private void copyAuthorizedPersonSnapshot(CardRequestVerification target, AuthorizedPersonRequestDto source) {
        target.setAuthorizedFirstName(source.getFirstName().trim());
        target.setAuthorizedLastName(source.getLastName().trim());
        target.setAuthorizedDateOfBirth(source.getDateOfBirth());
        target.setAuthorizedGender(source.getGender());
        target.setAuthorizedEmail(source.getEmail().trim());
        target.setAuthorizedPhone(source.getPhone().trim());
        target.setAuthorizedAddress(source.getAddress().trim());
    }

    /**
     * Schedules the verification email to be sent only after the current transaction commits successfully.

     * Why after commit:
     * if the transaction rolls back, the user must not receive a code for a verification request that was never stored.

     * Example:
     * this method is used right after saving {@link CardRequestVerification} in the initiation phase.
     *
     * @param recipient owner recipient details loaded from client-service
     * @param verification stored verification request
     * @param verificationCode plain one-time code that will be sent by email
     */
    private void registerAfterCommitVerificationEmail(
            ClientNotificationRecipientDto recipient,
            CardRequestVerification verification,
            String verificationCode
    ) {
        NotificationRecipient ownerRecipient = new NotificationRecipient(recipient.displayName(), recipient.email());
        CardNotificationDto payload = new CardNotificationDto(
                ownerRecipient.name(),
                ownerRecipient.email(),
                verificationTemplateVariables(verification, verificationCode)
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendCardNotification(CardNotificationType.CARD_REQUEST_VERIFICATION, payload);
            }
        });
    }

    /**
     * Schedules success notifications after the transaction commits successfully.

     * In the personal flow this is sent only to the owner.
     * In the business-authorized-person flow it may be sent to both the owner and the authorized person.

     * Why after commit:
     * the notification must reflect a card that was actually persisted.
     *
     * @param ownerRecipient owner notification recipient
     * @param authorizedRecipient optional authorized-person recipient
     * @param card newly created persisted card
     */
    private void registerAfterCommitRequestSuccess(
            ClientNotificationRecipientDto ownerRecipient,
            NotificationRecipient authorizedRecipient,
            Card card
    ) {
        Set<NotificationRecipient> recipients = new LinkedHashSet<>();
        recipients.add(new NotificationRecipient(ownerRecipient.displayName(), ownerRecipient.email()));
        if (authorizedRecipient != null) {
            recipients.add(authorizedRecipient);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recipients.forEach(recipient -> rabbitClient.sendCardNotification(
                        CardNotificationType.CARD_REQUEST_SUCCESS,
                        new CardNotificationDto(
                                recipient.name(),
                                recipient.email(),
                                successTemplateVariables(card)
                        )
                ));
            }
        });
    }

    /**
     * Sends an immediate failure notification when completion fails after a verification request already exists.

     * This is not delayed until transaction commit because the purpose is to inform the user that completion failed.

     * @param verification stored verification request
     * @param authorizedRecipient optional authorized-person recipient
     * @param reason human-readable failure reason
     */
    private void sendFailureNotification(
            CardRequestVerification verification,
            NotificationRecipient authorizedRecipient,
            String reason
    ) {
        ClientNotificationRecipientDto ownerRecipient = clientService.getNotificationRecipient(verification.getClientId());
        Set<NotificationRecipient> recipients = new LinkedHashSet<>();
        recipients.add(new NotificationRecipient(ownerRecipient.displayName(), ownerRecipient.email()));
        if (authorizedRecipient != null) {
            recipients.add(authorizedRecipient);
        }
        Map<String, String> variables = failureTemplateVariables(verification, reason);
        recipients.forEach(recipient -> rabbitClient.sendCardNotification(
                CardNotificationType.CARD_REQUEST_FAILURE,
                new CardNotificationDto(recipient.name(), recipient.email(), variables)
        ));
    }

    /**
     * Builds the template-variable map for verification emails in the manual request flow.
     *
     * @param verification stored verification request
     * @param verificationCode one-time code that completes the flow
     * @return ordered template-variable map for the verification notification
     */
    private Map<String, String> verificationTemplateVariables(
            CardRequestVerification verification,
            String verificationCode
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("verificationCode", verificationCode);
        variables.put("accountNumber", SensitiveDataMasker.maskAccountNumber(verification.getAccountNumber()));
        variables.put("cardName", verification.getCardBrand().toCardName());
        return variables;
    }

    /**
     * Builds the template-variable map for success notifications after card creation completes.
     *
     * @param card newly created card
     * @return ordered template-variable map for the success notification
     */
    private Map<String, String> successTemplateVariables(Card card) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("cardNumber", SensitiveDataMasker.maskCardNumber(card.getCardNumber()));
        variables.put("accountNumber", SensitiveDataMasker.maskAccountNumber(card.getAccountNumber()));
        variables.put("cardName", card.getCardName());
        return variables;
    }

    /**
     * Builds the template-variable map for failure notifications sent after completion errors.
     *
     * <p>When the triggering exception does not provide a usable message, a generic fallback
     * reason is inserted so the downstream template always receives a value.
     *
     * @param verification stored verification request
     * @param reason human-readable failure reason
     * @return ordered template-variable map for the failure notification
     */
    private Map<String, String> failureTemplateVariables(CardRequestVerification verification, String reason) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("accountNumber", SensitiveDataMasker.maskAccountNumber(verification.getAccountNumber()));
        variables.put("cardName", verification.getCardBrand().toCardName());
        variables.put("reason", reason == null || reason.isBlank() ? "Card creation failed." : reason);
        return variables;
    }

    /**
     * Requires a non-blank text value and throws the supplied business error when validation fails.
     *
     * @param value text value to validate
     * @param errorCode application-specific error code to throw on failure
     * @param message error message to propagate on failure
     */
    private void requireText(String value, ErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * Adapts an authorized-person entity to the lightweight notification-recipient shape.
     *
     * @param authorizedPerson resolved authorized person entity
     * @return notification recipient containing display name and email
     */
    private NotificationRecipient toNotificationRecipient(AuthorizedPerson authorizedPerson) {
        return new NotificationRecipient(
                (authorizedPerson.getFirstName() + " " + authorizedPerson.getLastName()).trim(),
                authorizedPerson.getEmail()
        );
    }

    /**
     * Adapts the authorized-person snapshot stored in a verification request to a notification recipient.
     *
     * @param verification stored verification request containing the approved recipient snapshot
     * @return notification recipient containing display name and email
     */
    private NotificationRecipient toNotificationRecipient(CardRequestVerification verification) {
        return new NotificationRecipient(
                (verification.getAuthorizedFirstName() + " " + verification.getAuthorizedLastName()).trim(),
                verification.getAuthorizedEmail()
        );
    }

    /**
     * @return random card type (VISA, MASTERCARD...), any random type of this 4 types
     */
    private CardBrand randomAutomaticCardBrand() {
        return AUTOMATIC_CARD_BRANDS[ThreadLocalRandom.current().nextInt(AUTOMATIC_CARD_BRANDS.length)];
    }

    /**
     * Lightweight in-memory representation of a notification recipient used during request flows.
     *
     * @param name recipient display name
     * @param email recipient email address
     */
    private record NotificationRecipient(String name, String email) {
    }
}
