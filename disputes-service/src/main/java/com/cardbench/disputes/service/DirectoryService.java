package com.cardbench.disputes.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.CardmemberRepository;
import com.cardbench.disputes.repository.PaymentCardRepository;

@Service
@Transactional(readOnly = true)
public class DirectoryService {
    private final CardmemberRepository cardmembers;
    private final PaymentCardRepository cards;
    private final CardTransactionRepository transactions;

    public DirectoryService(CardmemberRepository cardmembers, PaymentCardRepository cards,
                            CardTransactionRepository transactions) {
        this.cardmembers = cardmembers;
        this.cards = cards;
        this.transactions = transactions;
    }

    public List<Cardmember> listCardmembers() {
        return cardmembers.findAll();
    }

    public Cardmember getCardmember(String id) {
        return cardmembers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cardmember not found: " + id));
    }

    public List<PaymentCard> listCards() {
        return cards.findAll();
    }

    public List<PaymentCard> cardsForCardmember(String cardmemberId) {
        getCardmember(cardmemberId);
        return cards.findByCardmemberId(cardmemberId);
    }

    public List<CardTransaction> listTransactions(String cardmemberId, String cardId) {
        if (cardId != null) {
            if (!cards.existsById(cardId)) {
                throw new ResourceNotFoundException("Card not found: " + cardId);
            }
            return transactions.findByCardId(cardId);
        }
        if (cardmemberId != null) {
            getCardmember(cardmemberId);
            return transactions.findByCardCardmemberId(cardmemberId);
        }
        return transactions.findAll();
    }
}
