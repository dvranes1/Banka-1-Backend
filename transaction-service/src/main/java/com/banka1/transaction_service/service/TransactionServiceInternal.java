package com.banka1.transaction_service.service;

import com.banka1.transaction_service.dto.request.NewPaymentDto;
import org.springframework.security.oauth2.jwt.Jwt;

public interface TransactionServiceInternal {
    Long create(Jwt jwt, NewPaymentDto newPaymentDto);
    void process(Jwt jwt,NewPaymentDto newPaymentDto);
}
