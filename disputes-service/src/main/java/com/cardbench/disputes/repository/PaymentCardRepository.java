package com.cardbench.disputes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cardbench.disputes.domain.PaymentCard;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, String> {
    List<PaymentCard> findByCardmemberId(String cardmemberId);
}
