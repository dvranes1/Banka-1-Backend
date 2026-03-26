package com.banka1.card_service.repository;

import com.banka1.card_service.domain.CardRequestVerification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pending card-request verification records.
 */
public interface CardRequestVerificationRepository extends JpaRepository<CardRequestVerification, Long> {
}
