package com.banka1.card_service.service.implementation;

import com.banka1.card_service.domain.AuthorizedPerson;
import com.banka1.card_service.domain.Card;
import com.banka1.card_service.domain.CardRequestVerification;
import com.banka1.card_service.domain.enums.AccountOwnershipType;
import com.banka1.card_service.domain.enums.AuthorizedPersonGender;
import com.banka1.card_service.domain.enums.CardBrand;
import com.banka1.card_service.domain.enums.CardRequestRecipientType;
import com.banka1.card_service.domain.enums.CardStatus;
import com.banka1.card_service.dto.card_creation.internal.CardCreationResult;
import com.banka1.card_service.dto.card_creation.internal.CreateCardCommand;
import com.banka1.card_service.dto.card_creation.request.AuthorizedPersonRequestDto;
import com.banka1.card_service.dto.card_creation.request.AutoCardCreationRequestDto;
import com.banka1.card_service.dto.card_creation.request.BusinessCardRequestDto;
import com.banka1.card_service.dto.card_creation.request.ClientCardRequestDto;
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
import com.banka1.card_service.service.CardVerificationCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardRequestServiceImplTest {

    @Mock
    private CardCreationService cardCreationService;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardRequestVerificationRepository cardRequestVerificationRepository;

    @Mock
    private AuthorizedPersonRepository authorizedPersonRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ClientService clientService;

    @Mock
    private CardVerificationCodeService cardVerificationCodeService;

    @Mock
    private RabbitClient rabbitClient;

    private CardRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new CardRequestServiceImpl(
                cardCreationService,
                cardRepository,
                cardRequestVerificationRepository,
                authorizedPersonRepository,
                accountService,
                clientService,
                cardVerificationCodeService,
                rabbitClient,
                new CardCreationResponseMapper()
        );
        ReflectionTestUtils.setField(service, "verificationExpirationMinutes", 15L);
        ReflectionTestUtils.setField(service, "automaticCardDefaultLimit", BigDecimal.valueOf(1_000_000));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void handleClientRequestInitiatesVerificationAndSchedulesEmail() {
        ClientCardRequestDto request = new ClientCardRequestDto();
        request.setAccountNumber("265000000000123456");
        request.setCardBrand(CardBrand.VISA);
        request.setCardLimit(BigDecimal.valueOf(2500));

        when(accountService.getAccountContext("265000000000123456"))
                .thenReturn(new AccountNotificationContextDto(AccountOwnershipType.PERSONAL, 1L));
        when(cardRepository.countByAccountNumberAndClientIdAndAuthorizedPersonIdIsNullAndStatusNot(
                "265000000000123456", 1L, CardStatus.DEACTIVATED
        )).thenReturn(1L);
        when(cardVerificationCodeService.generateCode()).thenReturn("123456");
        when(cardVerificationCodeService.sha256Hex("123456")).thenReturn("hash-123456");
        when(cardRequestVerificationRepository.save(any(CardRequestVerification.class))).thenAnswer(invocation -> {
            CardRequestVerification verification = invocation.getArgument(0);
            verification.setId(99L);
            return verification;
        });
        when(clientService.getNotificationRecipient(1L)).thenReturn(ownerRecipient());

        var response = service.processManualCardRequest(1L, request);

        assertEquals("PENDING_VERIFICATION", response.status());
        assertEquals(99L, response.verificationRequestId());
        assertNull(response.createdCard());
        verify(rabbitClient, never()).sendCardNotification(any(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(rabbitClient, times(1)).sendCardNotification(
                eq(com.banka1.card_service.dto.enums.CardNotificationType.CARD_REQUEST_VERIFICATION),
                any()
        );
    }

    @Test
    void handleClientRequestCompletesVerificationCreatesCardAndSchedulesSuccessEmail() {
        ClientCardRequestDto request = new ClientCardRequestDto();
        request.setVerificationRequestId(10L);
        request.setVerificationCode("123456");

        CardRequestVerification verification = new CardRequestVerification();
        verification.setId(10L);
        verification.setAccountNumber("265000000000123456");
        verification.setClientId(1L);
        verification.setOwnershipType(AccountOwnershipType.PERSONAL);
        verification.setRecipientType(CardRequestRecipientType.OWNER);
        verification.setCardBrand(CardBrand.VISA);
        verification.setCardLimit(BigDecimal.valueOf(1200));
        verification.setVerificationCodeHash("hash-123456");
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        Card createdCard = card(501L, 1L, null);

        when(cardRequestVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));
        when(cardVerificationCodeService.sha256Hex("123456")).thenReturn("hash-123456");
        when(cardRepository.countByAccountNumberAndClientIdAndAuthorizedPersonIdIsNullAndStatusNot(
                "265000000000123456",
                1L,
                CardStatus.DEACTIVATED
        )).thenReturn(1L);
        when(cardCreationService.createCard(any(CreateCardCommand.class)))
                .thenReturn(new CardCreationResult(createdCard, "555"));
        when(cardRequestVerificationRepository.save(any(CardRequestVerification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clientService.getNotificationRecipient(1L)).thenReturn(ownerRecipient());

        var response = service.processManualCardRequest(1L, request);

        assertEquals("COMPLETED", response.status());
        assertEquals("4111111111111111", response.createdCard().cardNumber());
        assertEquals("555", response.createdCard().plainCvv());
        assertTrue(verification.isConsumed());
        verify(rabbitClient, never()).sendCardNotification(any(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(rabbitClient, times(1)).sendCardNotification(
                eq(com.banka1.card_service.dto.enums.CardNotificationType.CARD_REQUEST_SUCCESS),
                any()
        );
    }

    @Test
    void handleBusinessRequestCompletesAuthorizedPersonFlowAndSchedulesTwoSuccessEmails() {
        BusinessCardRequestDto request = new BusinessCardRequestDto();
        request.setVerificationRequestId(15L);
        request.setVerificationCode("654321");

        CardRequestVerification verification = new CardRequestVerification();
        verification.setId(15L);
        verification.setAccountNumber("265000000000999999");
        verification.setClientId(1L);
        verification.setOwnershipType(AccountOwnershipType.BUSINESS);
        verification.setRecipientType(CardRequestRecipientType.AUTHORIZED_PERSON);
        verification.setCardBrand(CardBrand.MASTERCARD);
        verification.setCardLimit(BigDecimal.valueOf(900));
        verification.setVerificationCodeHash("hash-654321");
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        verification.setAuthorizedPersonId(7L);
        verification.setAuthorizedFirstName("Ana");
        verification.setAuthorizedLastName("Anic");
        verification.setAuthorizedEmail("ana@example.com");
        verification.setAuthorizedPhone("0601234567");
        verification.setAuthorizedAddress("Adresa 1");
        verification.setAuthorizedDateOfBirth(LocalDate.of(1994, 2, 10));
        verification.setAuthorizedGender(AuthorizedPersonGender.FEMALE);

        AuthorizedPerson authorizedPerson = new AuthorizedPerson();
        authorizedPerson.setId(7L);
        authorizedPerson.setFirstName("Ana");
        authorizedPerson.setLastName("Anic");
        authorizedPerson.setEmail("ana@example.com");
        authorizedPerson.setPhone("0601234567");
        authorizedPerson.setAddress("Adresa 1");
        authorizedPerson.setDateOfBirth(LocalDate.of(1994, 2, 10));
        authorizedPerson.setGender(AuthorizedPersonGender.FEMALE);

        Card createdCard = card(777L, 1L, 7L);

        when(cardRequestVerificationRepository.findById(15L)).thenReturn(Optional.of(verification));
        when(cardVerificationCodeService.sha256Hex("654321")).thenReturn("hash-654321");
        when(authorizedPersonRepository.findById(7L)).thenReturn(Optional.of(authorizedPerson));
        when(cardRepository.countByAccountNumberAndAuthorizedPersonIdAndStatusNot(
                "265000000000999999", 7L, CardStatus.DEACTIVATED
        )).thenReturn(0L);
        when(cardCreationService.createCard(any(CreateCardCommand.class)))
                .thenReturn(new CardCreationResult(createdCard, "123"));
        when(authorizedPersonRepository.save(any(AuthorizedPerson.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRequestVerificationRepository.save(any(CardRequestVerification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clientService.getNotificationRecipient(1L)).thenReturn(ownerRecipient());

        var response = service.processBusinessCardRequest(1L, request);

        assertEquals("COMPLETED", response.status());
        assertEquals(1, authorizedPerson.getCardIds().size());
        assertEquals(777L, authorizedPerson.getCardIds().getFirst());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(rabbitClient, times(2)).sendCardNotification(
                eq(com.banka1.card_service.dto.enums.CardNotificationType.CARD_REQUEST_SUCCESS),
                any()
        );
    }

    @Test
    void handleBusinessRequestInitiationReusesExistingAuthorizedPersonWhenIdentityMatches() {
        BusinessCardRequestDto request = new BusinessCardRequestDto();
        request.setAccountNumber("265000000000999999");
        request.setRecipientType(CardRequestRecipientType.AUTHORIZED_PERSON);
        request.setCardBrand(CardBrand.VISA);
        request.setCardLimit(BigDecimal.valueOf(300));
        request.setAuthorizedPerson(authorizedPersonRequest());

        AuthorizedPerson existing = new AuthorizedPerson();
        existing.setId(20L);
        existing.setFirstName("Ana");
        existing.setLastName("Anic");
        existing.setEmail("ana@example.com");
        existing.setDateOfBirth(LocalDate.of(1994, 2, 10));

        when(accountService.getAccountContext("265000000000999999"))
                .thenReturn(new AccountNotificationContextDto(AccountOwnershipType.BUSINESS, 1L));
        when(authorizedPersonRepository.findByEmailIgnoreCaseAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
                "ana@example.com",
                "Ana",
                "Anic",
                LocalDate.of(1994, 2, 10)
        )).thenReturn(Optional.of(existing));
        when(cardRepository.countByAccountNumberAndAuthorizedPersonIdAndStatusNot(
                "265000000000999999", 20L, CardStatus.DEACTIVATED
        )).thenReturn(0L);
        when(cardVerificationCodeService.generateCode()).thenReturn("456789");
        when(cardVerificationCodeService.sha256Hex("456789")).thenReturn("hash-456789");
        when(cardRequestVerificationRepository.save(any(CardRequestVerification.class))).thenAnswer(invocation -> {
            CardRequestVerification verification = invocation.getArgument(0);
            verification.setId(33L);
            return verification;
        });
        when(clientService.getNotificationRecipient(1L)).thenReturn(ownerRecipient());

        var response = service.processBusinessCardRequest(1L, request);

        assertEquals("PENDING_VERIFICATION", response.status());
        ArgumentCaptor<CardRequestVerification> captor = ArgumentCaptor.forClass(CardRequestVerification.class);
        verify(cardRequestVerificationRepository).save(captor.capture());
        assertEquals(20L, captor.getValue().getAuthorizedPersonId());
    }

    @Test
    void createAutomaticCardUsesConfiguredDefaultLimitAndRandomSupportedBrand() {
        AutoCardCreationRequestDto request = new AutoCardCreationRequestDto();
        request.setAccountNumber("265000000000123456");
        request.setClientId(1L);

        Card createdCard = card(900L, 1L, null);
        createdCard.setCardLimit(BigDecimal.valueOf(1_000_000));

        when(cardCreationService.createCard(any(CreateCardCommand.class)))
                .thenReturn(new CardCreationResult(createdCard, "321"));

        service.createAutomaticCard(request);

        ArgumentCaptor<CreateCardCommand> captor = ArgumentCaptor.forClass(CreateCardCommand.class);
        verify(cardCreationService).createCard(captor.capture());
        assertTrue(Arrays.asList(CardBrand.values()).contains(captor.getValue().cardBrand()));
        assertEquals(BigDecimal.valueOf(1_000_000), captor.getValue().cardLimit());
    }

    private AuthorizedPersonRequestDto authorizedPersonRequest() {
        AuthorizedPersonRequestDto dto = new AuthorizedPersonRequestDto();
        dto.setFirstName("Ana");
        dto.setLastName("Anic");
        dto.setDateOfBirth(LocalDate.of(1994, 2, 10));
        dto.setGender(AuthorizedPersonGender.FEMALE);
        dto.setEmail("ana@example.com");
        dto.setPhone("0601234567");
        dto.setAddress("Adresa 1");
        return dto;
    }

    private ClientNotificationRecipientDto ownerRecipient() {
        return new ClientNotificationRecipientDto(1L, "Pera", "Peric", "pera@example.com");
    }

    private Card card(Long id, Long clientId, Long authorizedPersonId) {
        Card card = new Card();
        card.setId(id);
        card.setCardNumber("4111111111111111");
        card.setAccountNumber("265000000000123456");
        card.setClientId(clientId);
        card.setAuthorizedPersonId(authorizedPersonId);
        card.setCardName("Visa Debit");
        card.setCreationDate(LocalDate.of(2026, 3, 25));
        card.setExpirationDate(LocalDate.of(2031, 3, 25));
        card.setStatus(CardStatus.ACTIVE);
        card.setCardLimit(BigDecimal.valueOf(1200));
        return card;
    }
}
