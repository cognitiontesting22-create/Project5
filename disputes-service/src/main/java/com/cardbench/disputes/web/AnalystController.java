package com.cardbench.disputes.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.service.DisputeService;
import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeQueueService;
import com.cardbench.disputes.domain.DisputeReason;

@Controller
public class AnalystController {
    private final DisputeService disputes;
    private final DisputeQueueService queue;
    private final DirectoryService directory;

    public AnalystController(DisputeService disputes, DisputeQueueService queue, DirectoryService directory) {
        this.disputes = disputes;
        this.queue = queue;
        this.directory = directory;
    }

    @GetMapping("/analyst/disputes")
    @Transactional(readOnly = true)
    public String list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) String cardmemberId,
            @RequestParam(required = false) String overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            Model model) {
        var request = queue.parseRequest(status, reasonCode, cardmemberId, overdue, filedFrom, filedTo,
                sort, direction, page, pageSize);
        var result = queue.queue(request);
        model.addAttribute("queue", result);
        model.addAttribute("statuses", DisputeStatus.values());
        model.addAttribute("reasons", DisputeReason.values());
        model.addAttribute("cardmembers", directory.listCardmembers());
        model.addAttribute("selectedStatuses", status == null ? List.of() : status);
        model.addAttribute("reasonCode", reasonCode);
        model.addAttribute("cardmemberId", cardmemberId);
        model.addAttribute("overdue", overdue);
        model.addAttribute("filedFrom", filedFrom);
        model.addAttribute("filedTo", filedTo);
        model.addAttribute("sort", request.sort().parameter());
        model.addAttribute("direction", request.direction().name());
        return "disputes";
    }

    @GetMapping("/analyst/disputes/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Model model) {
        var dispute = disputes.getDispute(id);
        model.addAttribute("dispute", dispute);
        model.addAttribute("statuses", dispute.getStatus().allowedTransitions());
        model.addAttribute("history", disputes.history(id));
        return "dispute";
    }

    @PostMapping("/analyst/disputes/{id}/status")
    public String setStatus(
            @PathVariable Long id,
            @RequestParam DisputeStatus status,
            @RequestParam(required = false) String resolutionNote,
            @RequestParam(required = false) String actor) {
        disputes.setStatus(id, status, resolutionNote, actor);
        return "redirect:/analyst/disputes/" + id;
    }

    @PostMapping("/analyst/disputes/{id}/notes")
    public String addNote(
            @PathVariable Long id,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "false") boolean internal,
            @RequestParam(required = false) String actor) {
        disputes.addNote(id, text, internal, actor);
        return "redirect:/analyst/disputes/" + id;
    }
}
