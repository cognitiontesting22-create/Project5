package com.cardbench.disputes.api;

import java.util.List;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

import com.cardbench.disputes.api.ApiViews.CardView;
import com.cardbench.disputes.api.ApiViews.CardAccountView;
import com.cardbench.disputes.api.ApiViews.CardmemberView;
import com.cardbench.disputes.api.ApiViews.DisputeView;
import com.cardbench.disputes.api.ApiViews.DisputeReasonView;
import com.cardbench.disputes.api.ApiViews.DisputeEventView;
import com.cardbench.disputes.api.ApiViews.QueuePageView;
import com.cardbench.disputes.api.ApiViews.TransactionView;
import com.cardbench.disputes.api.DisputeRequests.FileDisputeRequest;
import com.cardbench.disputes.api.DisputeRequests.AddNoteRequest;
import com.cardbench.disputes.api.DisputeRequests.SetStatusRequest;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeService;
import com.cardbench.disputes.service.CardAccountService;
import com.cardbench.disputes.service.DisputeQueueService;

@RestController
@Validated
@RequestMapping("/api")
public class ApiController {
    private final DirectoryService directory;
    private final DisputeService disputeService;
    private final CardAccountService cardAccountService;
    private final DisputeQueueService queueService;

    public ApiController(DirectoryService directory, DisputeService disputeService,
                         CardAccountService cardAccountService, DisputeQueueService queueService) {
        this.directory = directory;
        this.disputeService = disputeService;
        this.cardAccountService = cardAccountService;
        this.queueService = queueService;
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

    @GetMapping("/cards/{cardId}/account")
    @Transactional(readOnly = true)
    public CardAccountView cardAccount(@PathVariable String cardId) {
        directory.getCard(cardId);
        return CardAccountView.from(cardId, cardAccountService.getAccount(cardId));
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
    public DisputeView fileDispute(
            @Valid @RequestBody FileDisputeRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return DisputeView.from(disputeService.fileDispute(
                request.transactionId(), request.amount(), request.reasonCode(), request.description(),
                actor));
    }

    @GetMapping("/dispute-reasons")
    public List<DisputeReasonView> disputeReasons() {
        return java.util.Arrays.stream(DisputeReason.values()).map(DisputeReasonView::from).toList();
    }

    @GetMapping("/disputes")
    @Transactional(readOnly = true)
    public List<DisputeView> disputes() {
        return disputeService.listDisputes().stream().map(DisputeView::from).toList();
    }

    @GetMapping("/disputes/queue")
    @Transactional(readOnly = true)
    public QueuePageView disputeQueue(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) String cardmemberId,
            @RequestParam(required = false) String overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        var request = queueService.parseRequest(status, reasonCode, cardmemberId, overdue, filedFrom, filedTo,
                sort, direction, page, pageSize);
        return QueuePageView.from(queueService.queue(request));
    }

    @GetMapping("/disputes/{id}")
    @Transactional(readOnly = true)
    public DisputeView dispute(@PathVariable Long id) {
        return DisputeView.from(disputeService.getDispute(id));
    }

    @GetMapping("/disputes/{id}/history")
    @Transactional(readOnly = true)
    public List<DisputeEventView> history(@PathVariable Long id) {
        return disputeService.history(id).stream().map(DisputeEventView::from).toList();
    }

    @PostMapping("/disputes/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DisputeEventView addNote(
            @PathVariable Long id,
            @RequestBody AddNoteRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return DisputeEventView.from(disputeService.addNote(id, request.text(), request.internal(), actor));
    }

    @PutMapping("/disputes/{id}/status")
    @Transactional
    public DisputeView setStatus(
            @PathVariable Long id,
            @Valid @RequestBody SetStatusRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        Dispute updated = disputeService.setStatus(id, request.status(), request.resolutionNote(), actor);
        return DisputeView.from(updated);
    }
}
