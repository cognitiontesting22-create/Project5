package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Service
public class DisputeService {
    private final DisputeRepository disputes;
    private final CardTransactionRepository transactions;

    public DisputeService(DisputeRepository disputes, CardTransactionRepository transactions) {
        this.disputes = disputes;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<Dispute> listDisputes() {
        return disputes.findAll();
    }

    @Transactional(readOnly = true)
    public Dispute getDispute(Long id) {
        return disputes.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + id));
    }

    @Transactional
    public Dispute fileDispute(String transactionId, BigDecimal amount, String reason) {
        CardTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        return disputes.save(new Dispute(transaction, amount, reason));
    }

    @Transactional
    public Dispute setStatus(Long id, DisputeStatus status) {
        Dispute dispute = getDispute(id);
        dispute.setStatus(status);
        return disputes.save(dispute);
    }
}
