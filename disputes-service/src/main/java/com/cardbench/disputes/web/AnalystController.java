package com.cardbench.disputes.web;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.service.DisputeService;

@Controller
public class AnalystController {
    private final DisputeService disputes;

    public AnalystController(DisputeService disputes) {
        this.disputes = disputes;
    }

    @GetMapping("/analyst/disputes")
    @Transactional(readOnly = true)
    public String list(Model model) {
        model.addAttribute("disputes", disputes.listDisputes());
        return "disputes";
    }

    @GetMapping("/analyst/disputes/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("dispute", disputes.getDispute(id));
        model.addAttribute("statuses", DisputeStatus.values());
        return "dispute";
    }

    @PostMapping("/analyst/disputes/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam DisputeStatus status) {
        disputes.setStatus(id, status);
        return "redirect:/analyst/disputes/" + id;
    }
}
