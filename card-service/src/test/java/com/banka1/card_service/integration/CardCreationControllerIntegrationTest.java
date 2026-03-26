package com.banka1.card_service.integration;

import com.banka1.card_service.domain.AuthorizedPerson;
import com.banka1.card_service.domain.Card;
import com.banka1.card_service.domain.CardRequestVerification;
import com.banka1.card_service.domain.enums.AccountOwnershipType;
import com.banka1.card_service.domain.enums.CardBrand;
import com.banka1.card_service.domain.enums.CardRequestRecipientType;
import com.banka1.card_service.domain.enums.CardStatus;
import com.banka1.card_service.dto.card_management.internal.CardNotificationDto;
import com.banka1.card_service.dto.enums.CardNotificationType;
import com.banka1.card_service.rabbitMQ.RabbitClient;
import com.banka1.card_service.repository.AuthorizedPersonRepository;
import com.banka1.card_service.repository.CardRepository;
import com.banka1.card_service.repository.CardRequestVerificationRepository;
import com.banka1.card_service.rest_client.AccountNotificationContextDto;
import com.banka1.card_service.rest_client.AccountService;
import com.banka1.card_service.rest_client.ClientNotificationRecipientDto;
import com.banka1.card_service.rest_client.ClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration coverage for the three card-creation controller endpoints.
 *
 * <p>These tests execute the real HTTP layer, validation, security filters,
 * service orchestration, persistence, and DTO mapping against the in-memory test database.
 * Only outbound dependencies are mocked:
 * account-service, client-service, and RabbitMQ publishing.
 *
 * <p>The goal is not only to verify HTTP statuses, but also to prove that:
 * request flows store verification state correctly,
 * card creation actually persists entities,
 * business flows materialize authorized persons when needed,
 * and notification payloads contain the values required to complete multi-step flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardCreationControllerIntegrationTest {

    private static final Long OWNER_CLIENT_ID = 1L;
    private static final String PERSONAL_ACCOUNT_NUMBER = "265000000000123456";
    private static final String BUSINESS_ACCOUNT_NUMBER = "265000000000999999";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CardRequestVerificationRepository cardRequestVerificationRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private AuthorizedPersonRepository authorizedPersonRepository;

    @Value("${card.creation.automatic.default-limit}")
    private BigDecimal automaticDefaultLimit;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private RabbitClient rabbitClient;

    @BeforeEach
    void setUp() {
        cardRepository.deleteAll();
        authorizedPersonRepository.deleteAll();
        cardRequestVerificationRepository.deleteAll();
        reset(accountService, clientService, rabbitClient);
    }

    /**
     * Verifies the internal automatic endpoint end to end.
     *
     * <p>A successful run proves that:
     * the service caller can hit {@code POST /auto},
     * a real card entity is persisted,
     * the configured automatic default limit is applied,
     * and the API returns the same persisted card number together with the one-time CVV.
     */
    @Test
    @DisplayName("POST /auto persists a real card and returns the one-time creation payload")
    void autoCreateCard_persistsCardAndReturnsCreationPayload() throws Exception {
        String requestBody = """
                {
                  "clientId": 1,
                  "accountNumber": "265000000000123456"
                }
                """;

        String responseBody = mockMvc.perform(post("/auto")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardNumber").exists())
                .andExpect(jsonPath("$.plainCvv").exists())
                .andExpect(jsonPath("$.expirationDate").exists())
                .andExpect(jsonPath("$.cardName").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(responseBody);
        List<Card> persistedCards = cardRepository.findByClientId(OWNER_CLIENT_ID);

        assertThat(persistedCards)
                .as("Automatic creation should persist exactly one card for the requested client.")
                .hasSize(1);

        Card persistedCard = persistedCards.get(0);
        assertThat(persistedCard.getAccountNumber())
                .as("The persisted card must stay linked to the input account number.")
                .isEqualTo(PERSONAL_ACCOUNT_NUMBER);
        assertThat(persistedCard.getClientId())
                .as("The persisted card must belong to the requested client.")
                .isEqualTo(OWNER_CLIENT_ID);
        assertThat(persistedCard.getAuthorizedPersonId())
                .as("Automatic creation must create a client-owned card, not an authorized-person card.")
                .isNull();
        assertThat(persistedCard.getCardLimit())
                .as("Automatic creation must apply the configured default spending limit.")
                .isEqualByComparingTo(automaticDefaultLimit);
        assertThat(persistedCard.getStatus())
                .as("A newly created automatic card must start in ACTIVE state.")
                .isEqualTo(CardStatus.ACTIVE);
        assertThat(ChronoUnit.YEARS.between(persistedCard.getCreationDate(), persistedCard.getExpirationDate()))
                .as("The card expiration must remain five years after creation.")
                .isEqualTo(5);
        assertThat(responseJson.get("cardNumber").asText())
                .as("The API must return the same card number that was persisted.")
                .isEqualTo(persistedCard.getCardNumber());
        assertThat(responseJson.get("cardName").asText())
                .as("The API must return the derived persisted card name.")
                .isEqualTo(persistedCard.getCardName());
        assertThat(responseJson.get("plainCvv").asText())
                .as("The one-time CVV returned to the caller must be a 3-digit value.")
                .matches("\\d{3}");
        assertThat(persistedCard.getCvv())
                .as("Only the hashed CVV should be stored in the database.")
                .isNotEqualTo(responseJson.get("plainCvv").asText());
    }

    /**
     * Verifies the personal manual flow through both controller phases.
     *
     * <p>A successful run proves that:
     * initiation stores a pending verification request in the database,
     * the verification notification exposes a usable code,
     * completion consumes the verification request,
     * and a real card row is persisted for the authenticated client.
     */
    @Test
    @DisplayName("POST /request initiates personal verification and then persists the card after confirmation")
    void manualPersonalCardRequest_initiatesVerificationAndCompletesCardCreation() throws Exception {
        when(accountService.getAccountContext(PERSONAL_ACCOUNT_NUMBER))
                .thenReturn(new AccountNotificationContextDto(AccountOwnershipType.PERSONAL, OWNER_CLIENT_ID));
        when(clientService.getNotificationRecipient(OWNER_CLIENT_ID))
                .thenReturn(ownerRecipient());

        String initiationPayload = """
                {
                  "accountNumber": "265000000000123456",
                  "cardBrand": "VISA",
                  "cardLimit": 1500.00
                }
                """;

        String initiationResponse = mockMvc.perform(post("/request")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiationPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.message").value("Verification code sent."))
                .andExpect(jsonPath("$.verificationRequestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long verificationRequestId = objectMapper.readTree(initiationResponse).get("verificationRequestId").asLong();
        CardRequestVerification pendingVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();

        assertThat(pendingVerification.isConsumed())
                .as("The verification request must stay open until the client submits the code.")
                .isFalse();
        assertThat(pendingVerification.getOwnershipType())
                .as("The stored verification row must identify this flow as PERSONAL.")
                .isEqualTo(AccountOwnershipType.PERSONAL);
        assertThat(pendingVerification.getRecipientType())
                .as("A personal request should always target the owner.")
                .isEqualTo(CardRequestRecipientType.OWNER);
        assertThat(pendingVerification.getAccountNumber())
                .as("The verification row must keep the target account number.")
                .isEqualTo(PERSONAL_ACCOUNT_NUMBER);
        assertThat(pendingVerification.getCardBrand())
                .as("The verification row must keep the requested card brand.")
                .isEqualTo(CardBrand.VISA);
        assertThat(pendingVerification.getCardLimit())
                .as("The verification row must keep the requested card limit.")
                .isEqualByComparingTo("1500.00");

        String verificationCode = captureVerificationCode();
        clearInvocations(rabbitClient);

        String completionPayload = """
                {
                  "verificationRequestId": %d,
                  "verificationCode": "%s"
                }
                """.formatted(verificationRequestId, verificationCode);

        String completionResponse = mockMvc.perform(post("/request")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Card created successfully."))
                .andExpect(jsonPath("$.createdCard.cardNumber").exists())
                .andExpect(jsonPath("$.createdCard.plainCvv").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completionJson = objectMapper.readTree(completionResponse);
        CardRequestVerification consumedVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();
        List<Card> persistedCards = cardRepository.findByClientId(OWNER_CLIENT_ID);

        assertThat(consumedVerification.isConsumed())
                .as("The verification row must be marked consumed after the card is created.")
                .isTrue();
        assertThat(persistedCards)
                .as("Successful completion must persist exactly one personal card.")
                .hasSize(1);

        Card persistedCard = persistedCards.get(0);
        assertThat(persistedCard.getAccountNumber())
                .as("The stored card must remain linked to the original personal account.")
                .isEqualTo(PERSONAL_ACCOUNT_NUMBER);
        assertThat(persistedCard.getAuthorizedPersonId())
                .as("Personal cards must not carry an authorized-person reference.")
                .isNull();
        assertThat(persistedCard.getCardLimit())
                .as("The persisted card must use the limit approved during verification.")
                .isEqualByComparingTo("1500.00");
        assertThat(completionJson.get("createdCard").get("cardNumber").asText())
                .as("The completion response must expose the card number that was saved.")
                .isEqualTo(persistedCard.getCardNumber());

        verify(rabbitClient, times(1)).sendCardNotification(eq(CardNotificationType.CARD_REQUEST_SUCCESS), eqNotificationForOwner());
    }

    /**
     * Verifies the business-owner scenario on the shared business endpoint.
     *
     * <p>A successful run proves that:
     * the owner flow stores a BUSINESS verification request,
     * completion persists a business card for the owner,
     * no authorized-person record is created,
     * and the success notification remains owner-only.
     */
    @Test
    @DisplayName("POST /request/business creates a business-owner card without creating an authorized person")
    void businessOwnerRequest_initiatesVerificationAndCreatesOwnerCard() throws Exception {
        when(accountService.getAccountContext(BUSINESS_ACCOUNT_NUMBER))
                .thenReturn(new AccountNotificationContextDto(AccountOwnershipType.BUSINESS, OWNER_CLIENT_ID));
        when(clientService.getNotificationRecipient(OWNER_CLIENT_ID))
                .thenReturn(ownerRecipient());

        String initiationPayload = """
                {
                  "accountNumber": "265000000000999999",
                  "recipientType": "OWNER",
                  "cardBrand": "DINACARD",
                  "cardLimit": 2500.00
                }
                """;

        String initiationResponse = mockMvc.perform(post("/request/business")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiationPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.verificationRequestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long verificationRequestId = objectMapper.readTree(initiationResponse).get("verificationRequestId").asLong();
        CardRequestVerification pendingVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();

        assertThat(pendingVerification.getOwnershipType())
                .as("The verification row must identify the owner business flow as BUSINESS.")
                .isEqualTo(AccountOwnershipType.BUSINESS);
        assertThat(pendingVerification.getRecipientType())
                .as("The recipient type must remain OWNER for the owner scenario.")
                .isEqualTo(CardRequestRecipientType.OWNER);
        assertThat(pendingVerification.getAuthorizedPersonId())
                .as("Owner flow should not snapshot or reference an authorized person.")
                .isNull();

        String verificationCode = captureVerificationCode();
        clearInvocations(rabbitClient);

        String completionPayload = """
                {
                  "verificationRequestId": %d,
                  "verificationCode": "%s"
                }
                """.formatted(verificationRequestId, verificationCode);

        String completionResponse = mockMvc.perform(post("/request/business")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdCard.cardNumber").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completionJson = objectMapper.readTree(completionResponse);
        CardRequestVerification consumedVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();
        List<Card> persistedCards = cardRepository.findByClientId(OWNER_CLIENT_ID);

        assertThat(consumedVerification.isConsumed())
                .as("The verification row must be consumed once the owner card is issued.")
                .isTrue();
        assertThat(authorizedPersonRepository.count())
                .as("Owner flow must not create an authorized-person row.")
                .isZero();
        assertThat(persistedCards)
                .as("Owner flow must persist exactly one business-owner card.")
                .hasSize(1);

        Card persistedCard = persistedCards.get(0);
        assertThat(persistedCard.getAuthorizedPersonId())
                .as("Owner-issued business cards must stay unlinked from authorized persons.")
                .isNull();
        assertThat(persistedCard.getCardLimit())
                .as("The owner card must persist the approved business limit.")
                .isEqualByComparingTo("2500.00");
        assertThat(completionJson.get("createdCard").get("cardNumber").asText())
                .as("The endpoint must return the persisted owner card number.")
                .isEqualTo(persistedCard.getCardNumber());

        verify(rabbitClient, times(1)).sendCardNotification(eq(CardNotificationType.CARD_REQUEST_SUCCESS), eqNotificationForOwner());
    }

    /**
     * Verifies the business authorized-person scenario through both phases.
     *
     * <p>A successful run proves that:
     * initiation stores an authorized-person snapshot in the verification table,
     * completion materializes a new authorized-person entity,
     * the created card is linked to that person,
     * the link is reflected back in the person's stored card IDs,
     * and both the owner and the authorized person receive success notifications.
     */
    @Test
    @DisplayName("POST /request/business creates an authorized person and links the new business card to them")
    void businessAuthorizedPersonRequest_materializesAuthorizedPersonAndPersistsLinkedCard() throws Exception {
        when(accountService.getAccountContext(BUSINESS_ACCOUNT_NUMBER))
                .thenReturn(new AccountNotificationContextDto(AccountOwnershipType.BUSINESS, OWNER_CLIENT_ID));
        when(clientService.getNotificationRecipient(OWNER_CLIENT_ID))
                .thenReturn(ownerRecipient());

        String initiationPayload = """
                {
                  "accountNumber": "265000000000999999",
                  "recipientType": "AUTHORIZED_PERSON",
                  "cardBrand": "MASTERCARD",
                  "cardLimit": 800.00,
                  "authorizedPerson": {
                    "firstName": "Ana",
                    "lastName": "Anic",
                    "dateOfBirth": "1994-02-10",
                    "gender": "FEMALE",
                    "email": "ana@example.com",
                    "phone": "0601234567",
                    "address": "Adresa 1"
                  }
                }
                """;

        String initiationResponse = mockMvc.perform(post("/request/business")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiationPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.verificationRequestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long verificationRequestId = objectMapper.readTree(initiationResponse).get("verificationRequestId").asLong();
        CardRequestVerification pendingVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();

        assertThat(pendingVerification.getRecipientType())
                .as("The verification row must preserve the AUTHORIZED_PERSON target.")
                .isEqualTo(CardRequestRecipientType.AUTHORIZED_PERSON);
        assertThat(pendingVerification.getAuthorizedPersonId())
                .as("A new authorized person should not exist yet during initiation.")
                .isNull();
        assertThat(pendingVerification.getAuthorizedFirstName())
                .as("The authorized-person snapshot must be stored until verification is completed.")
                .isEqualTo("Ana");
        assertThat(pendingVerification.getAuthorizedEmail())
                .as("The snapshot must preserve the authorized person's email for later materialization.")
                .isEqualTo("ana@example.com");

        String verificationCode = captureVerificationCode();
        clearInvocations(rabbitClient);

        String completionPayload = """
                {
                  "verificationRequestId": %d,
                  "verificationCode": "%s"
                }
                """.formatted(verificationRequestId, verificationCode);

        String completionResponse = mockMvc.perform(post("/request/business")
                        .with(clientJwt(OWNER_CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdCard.cardNumber").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode completionJson = objectMapper.readTree(completionResponse);
        CardRequestVerification consumedVerification = cardRequestVerificationRepository.findById(verificationRequestId).orElseThrow();
        List<Card> persistedCards = cardRepository.findByClientId(OWNER_CLIENT_ID);
        List<AuthorizedPerson> authorizedPeople = authorizedPersonRepository.findAll();

        assertThat(consumedVerification.isConsumed())
                .as("The verification row must be consumed after the authorized-person card is created.")
                .isTrue();
        assertThat(authorizedPeople)
                .as("Completion must materialize exactly one authorized person from the stored snapshot.")
                .hasSize(1);
        assertThat(persistedCards)
                .as("Completion must persist exactly one business card for the owner account.")
                .hasSize(1);

        AuthorizedPerson authorizedPerson = authorizedPeople.get(0);
        Card persistedCard = persistedCards.get(0);

        assertThat(authorizedPerson.getFirstName())
                .as("The newly created authorized-person row must use the snapshot first name.")
                .isEqualTo("Ana");
        assertThat(authorizedPerson.getLastName())
                .as("The newly created authorized-person row must use the snapshot last name.")
                .isEqualTo("Anic");
        assertThat(authorizedPerson.getDateOfBirth())
                .as("The newly created authorized-person row must use the snapshot birth date.")
                .isEqualTo(LocalDate.of(1994, 2, 10));
        assertThat(authorizedPerson.getEmail())
                .as("The newly created authorized-person row must use the snapshot email.")
                .isEqualTo("ana@example.com");
        assertThat(persistedCard.getAuthorizedPersonId())
                .as("The created card must be linked to the newly materialized authorized person.")
                .isEqualTo(authorizedPerson.getId());
        assertThat(authorizedPerson.getCardIds())
                .as("The authorized person must keep the ID of the newly issued card.")
                .containsExactly(persistedCard.getId());
        assertThat(completionJson.get("createdCard").get("cardNumber").asText())
                .as("The completion response must return the persisted authorized-person card number.")
                .isEqualTo(persistedCard.getCardNumber());

        var notificationCaptor = org.mockito.ArgumentCaptor.forClass(CardNotificationDto.class);
        verify(rabbitClient, times(2)).sendCardNotification(eq(CardNotificationType.CARD_REQUEST_SUCCESS), notificationCaptor.capture());

        assertThat(notificationCaptor.getAllValues())
                .as("Authorized-person success flow must notify both the owner and the authorized person.")
                .extracting(CardNotificationDto::getUserEmail)
                .containsExactlyInAnyOrder("pera@example.com", "ana@example.com");
    }

    private String captureVerificationCode() {
        var notificationCaptor = org.mockito.ArgumentCaptor.forClass(CardNotificationDto.class);
        verify(rabbitClient, times(1)).sendCardNotification(eq(CardNotificationType.CARD_REQUEST_VERIFICATION), notificationCaptor.capture());
        String verificationCode = notificationCaptor.getValue().getTemplateVariables().get("verificationCode");
        assertThat(verificationCode)
                .as("The verification notification must contain the plain code needed to complete the flow.")
                .matches("\\d{6}");
        return verificationCode;
    }

    private CardNotificationDto eqNotificationForOwner() {
        return org.mockito.ArgumentMatchers.argThat(notification ->
                notification != null
                        && "pera@example.com".equals(notification.getUserEmail())
                        && notification.getTemplateVariables() != null
                        && notification.getTemplateVariables().containsKey("cardNumber")
        );
    }

    private ClientNotificationRecipientDto ownerRecipient() {
        return new ClientNotificationRecipientDto(OWNER_CLIENT_ID, "Pera", "Peric", "pera@example.com");
    }

    private RequestPostProcessor clientJwt(Long clientId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))
                .jwt(token -> token.claim("id", clientId));
    }

    private RequestPostProcessor serviceJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))
                .jwt(token -> token.claim("id", 999L));
    }
}
