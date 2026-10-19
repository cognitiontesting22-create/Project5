package com.cardbench.disputes.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

import com.cardbench.disputes.api.ApiViews.CardView;
import com.cardbench.disputes.api.ApiViews.CardmemberView;
import com.cardbench.disputes.api.ApiViews.DisputeView;
import com.cardbench.disputes.api.ApiViews.TransactionView;
import com.cardbench.disputes.api.DisputeRequests.FileDisputeRequest;
import com.cardbench.disputes.api.DisputeRequests.SetStatusRequest;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeService;

@RestController
@Validated
@RequestMapping("/api")
public class ApiController {
    private final DirectoryService directory;
    private final DisputeService disputeService;

    public ApiController(DirectoryService directory, DisputeService disputeService) {
        this.directory = directory;
        this.disputeService = disputeService;
    }

    @GetMapping("/cardmembers")
    @Transactional(readOnly = true)
    public List<CardmemberView> cardmembers() {
        return directory.listCardmembers().stream().map(CardmemberView::from).toList();
    }

    @GetMapping("/cards")
    @Transactional(readOnly = true)
    public List<CardView> cards(@RequestParam(required = false) String cardmemberId) {
        return (cardmemberId == null ? directory.listCards() : directory.cardsForCardmember(cardmemberId))
                .stream().map(CardView::from).toList();
    }

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public List<TransactionView> transactions(
            @RequestParam(required = false) String cardmemberId,
            @RequestParam(required = false) String cardId) {
        return directory.listTransactions(cardmemberId, cardId).stream().map(TransactionView::from).toList();
    }

    @PostMapping("/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DisputeView fileDispute(@Valid @RequestBody FileDisputeRequest request) {
        return DisputeView.from(disputeService.fileDispute(
                request.transactionId(), request.amount(), request.reason()));
    }

    @GetMapping("/disputes")
    @Transactional(readOnly = true)
    public List<DisputeView> disputes() {
        return disputeService.listDisputes().stream().map(DisputeView::from).toList();
    }

    @GetMapping("/disputes/{id}")
    @Transactional(readOnly = true)
    public DisputeView dispute(@PathVariable Long id) {
        return DisputeView.from(disputeService.getDispute(id));
    }

    @PutMapping("/disputes/{id}/status")
    @Transactional
    public DisputeView setStatus(@PathVariable Long id, @Valid @RequestBody SetStatusRequest request) {
        Dispute updated = disputeService.setStatus(id, request.status(), request.resolutionNote());
        return DisputeView.from(updated);
    }
}
