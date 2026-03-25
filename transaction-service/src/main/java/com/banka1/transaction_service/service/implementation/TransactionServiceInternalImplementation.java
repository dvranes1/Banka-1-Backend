package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.Payment;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.repository.PaymentRepository;
import com.banka1.transaction_service.service.TransactionServiceInternal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Setter
@Getter
@Transactional
public class TransactionServiceInternalImplementation implements TransactionServiceInternal {
    private final PaymentRepository paymentRepository;

    @Override
    public Long create(Jwt jwt, NewPaymentDto newPaymentDto) {
        Payment payment=new Payment();
        payment.setFromAccountNumber(newPaymentDto.getFromAccountNumber());
        payment.setToAccountNumber(newPaymentDto.getToAccountNumber());
        payment.setStatus(TransactionStatus.IN_PROGRESS);
        payment.setInitialAmount(newPaymentDto.getAmount());
        //payment.setRecipientClientId();
        payment=paymentRepository.save(payment);
        payment.setOrderNumber("BANKA1-"+payment.getId());
        return payment.getId();
    }

    @Override
    public void process(Jwt jwt, NewPaymentDto newPaymentDto) {

    }
}
