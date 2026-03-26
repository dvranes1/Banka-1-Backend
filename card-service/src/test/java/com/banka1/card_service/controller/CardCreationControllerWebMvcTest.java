package com.banka1.card_service.controller;

import com.banka1.card_service.advice.GlobalExceptionHandler;
import com.banka1.card_service.dto.card_creation.request.AutoCardCreationRequestDto;
import com.banka1.card_service.dto.card_creation.response.CardCreationResponseDto;
import com.banka1.card_service.dto.card_creation.response.CardRequestResponseDto;
import com.banka1.card_service.service.CardRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardCreationController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, CardControllerSupport.class})
@ActiveProfiles("test")
class CardCreationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private CardRequestService cardRequestService;

    @Test
    void autoCreateCardReturnsCreatedForInternalCaller() throws Exception {
        AutoCardCreationRequestDto request = new AutoCardCreationRequestDto();
        request.setAccountNumber("265000000000123456");
        request.setClientId(1L);

        CardCreationResponseDto response = new CardCreationResponseDto(
                "5798123456785571",
                "123",
                LocalDate.of(2031, 3, 20),
                "Visa Debit"
        );
        when(cardRequestService.createAutomaticCard(any(AutoCardCreationRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/auto")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))
                                .jwt(jwt -> jwt.claim("id", 999L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardNumber").value("5798123456785571"));

        verify(cardRequestService).createAutomaticCard(any(AutoCardCreationRequestDto.class));
    }

    @Test
    void requestCardReturnsAcceptedWhenVerificationIsPending() throws Exception {
        String requestBody = """
                {
                  "accountNumber": "265000000000123456",
                  "cardBrand": "VISA",
                  "cardLimit": 1500
                }
                """;

        CardRequestResponseDto response = new CardRequestResponseDto(
                "PENDING_VERIFICATION",
                "Verification required.",
                11L,
                null
        );
        when(cardRequestService.processManualCardRequest(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(post("/request")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))
                                .jwt(jwt -> jwt.claim("id", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.verificationRequestId").value(11L));

        verify(cardRequestService).processManualCardRequest(eq(1L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestBusinessCardReturnsCreatedWhenCardIsIssued() throws Exception {
        String requestBody = """
                {
                  "verificationRequestId": 15,
                  "verificationCode": "654321"
                }
                """;

        CardRequestResponseDto response = new CardRequestResponseDto(
                "COMPLETED",
                "Card created.",
                null,
                new CardCreationResponseDto(
                        "5798123456785571",
                        "123",
                        LocalDate.of(2031, 3, 20),
                        "Visa Debit"
                )
        );
        when(cardRequestService.processBusinessCardRequest(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(post("/request/business")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))
                                .jwt(jwt -> jwt.claim("id", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCard.cardNumber").value("5798123456785571"));

        verify(cardRequestService).processBusinessCardRequest(eq(1L), org.mockito.ArgumentMatchers.any());
    }
}
