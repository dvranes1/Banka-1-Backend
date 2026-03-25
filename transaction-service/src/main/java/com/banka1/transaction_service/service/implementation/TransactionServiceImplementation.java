package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.domain.enums.VerificationStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.request.ValidateRequest;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.dto.response.ValidateResponse;
import com.banka1.transaction_service.exception.BusinessException;
import com.banka1.transaction_service.exception.ErrorCode;
import com.banka1.transaction_service.rest_client.VerificationService;
import com.banka1.transaction_service.service.TransactionService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@RequiredArgsConstructor
@Getter
@Setter
@Service
public class TransactionServiceImplementation implements TransactionService {

    private final VerificationService verificationService;

    @Transactional
    @Override
    public String newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
        ValidateResponse validateResponse=verificationService.validate(new ValidateRequest(newPaymentDto.getVerificationSessionId(),newPaymentDto.getVerificationCode()));
        if(validateResponse==null || validateResponse.getStatus()!= VerificationStatus.VERIFIED)
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED,ErrorCode.VERIFICATION_FAILED.getTitle());
        return "";
    }

    @Override
    public String approveTransaction(Jwt jwt, Long id, ApproveDto approveDto) {
        return "";
    }

    //todo za sad ovo ostavljam ovde, validacije bi trebalo da budu zaseban servis, if-ove sam ostavio just in case
    //TODO menjati exceptione

//    private void  validation(AccountDto account,Jwt jwt)
//    {
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji unet racun");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//    }


    @Override
    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, Long id, TransactionStatus transactionStatus, LocalDate fromDate, LocalDate toDate, BigDecimal minAmount, BigDecimal maxAmount, int page, int size) {
        //validate(account,jwt);
        return null;
    }



}
