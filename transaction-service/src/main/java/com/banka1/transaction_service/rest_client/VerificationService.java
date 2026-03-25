package com.banka1.transaction_service.rest_client;



import com.banka1.transaction_service.dto.request.ValidateRequest;
import com.banka1.transaction_service.dto.response.ValidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final RestClient restClient;

    public ValidateResponse validate(ValidateRequest request)
    {
        return restClient.post()
                .uri("/validate")
                .body(request)
                .retrieve()
                .body(ValidateResponse.class);
    }
}
