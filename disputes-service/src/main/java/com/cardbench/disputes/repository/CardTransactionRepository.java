package com.cardbench.disputes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cardbench.disputes.domain.CardTransaction;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, String> {
    List<CardTransaction> findByCardId(String cardId);

    List<CardTransaction> findByCardCardmemberId(String cardmemberId);
}
