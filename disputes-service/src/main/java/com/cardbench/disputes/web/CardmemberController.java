package com.cardbench.disputes.web;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeService;
import com.cardbench.disputes.service.DisputeFilingException;
import com.cardbench.disputes.service.ResourceNotFoundException;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.service.CardAccountService;
import com.cardbench.disputes.domain.DisputeEvent;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CardmemberController {
    private final DirectoryService directory;
    private final DisputeService disputes;
    private final CardAccountService accounts;

    public CardmemberController(DirectoryService directory, DisputeService disputes, CardAccountService accounts) {
        this.directory = directory;
        this.disputes = disputes;
        this.accounts = accounts;
    }

    @GetMapping("/cardmembers/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("cardmember", directory.getCardmember(id));
        model.addAttribute("cards", directory.cardsForCardmember(id));
        var transactions = directory.listTransactions(id, null);
        model.addAttribute("transactions", transactions);
        model.addAttribute("disputes", disputes.listDisputesForCardmember(id));
        model.addAttribute("disputeHistory", historyByDispute(disputes.historyForCardmember(id)));
        model.addAttribute("filingBlockReasons", disputes.filingBlockReasons(transactions));
        model.addAttribute("disputeReasons", Arrays.stream(DisputeReason.values()).toList());
        return "cardmember";
    }

    @GetMapping("/cardmembers/{id}/cards/{cardId}")
    @Transactional(readOnly = true)
    public String cardAccount(@PathVariable String id, @PathVariable String cardId, Model model) {
        var cardmember = directory.getCardmember(id);
        var card = directory.getCard(cardId);
        if (!card.getCardmember().getId().equals(cardmember.getId())) {
            throw new ResourceNotFoundException("Card not found: " + cardId);
        }
        model.addAttribute("cardmember", cardmember);
        model.addAttribute("card", card);
        model.addAttribute("account", accounts.getAccount(cardId));
        return "card-account";
    }

    @PostMapping("/cardmembers/{id}/disputes")
    public String fileDispute(
            @PathVariable String id,
            @RequestParam String transactionId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "cardmember") String actor,
            RedirectAttributes redirectAttributes) {
        directory.getCardmember(id);
        try {
            var dispute = disputes.fileDispute(transactionId, amount, reasonCode, description, actor);
            redirectAttributes.addFlashAttribute("notice", "Dispute filed.");
            return "redirect:/analyst/disputes/" + dispute.getId();
        } catch (DisputeFilingException exception) {
            redirectAttributes.addFlashAttribute("filingError", exception.getMessage());
            redirectAttributes.addFlashAttribute("filingErrorTransactionId", transactionId);
            return "redirect:/cardmembers/" + id;
        }
    }

    @PostMapping("/cardmembers/{id}/disputes/{disputeId}/withdraw")
    public String withdrawDispute(
            @PathVariable String id,
            @PathVariable Long disputeId,
            @RequestParam(defaultValue = "cardmember") String actor,
            RedirectAttributes redirectAttributes) {
        directory.getCardmember(id);
        disputes.withdrawDisputeForCardmember(disputeId, id, actor);
        redirectAttributes.addFlashAttribute("notice", "Dispute withdrawn.");
        return "redirect:/cardmembers/" + id;
    }

    private java.util.Map<Long, java.util.List<DisputeEvent>> historyByDispute(List<DisputeEvent> events) {
        return events.stream().collect(java.util.stream.Collectors.groupingBy(
                event -> event.getDispute().getId(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }
}
