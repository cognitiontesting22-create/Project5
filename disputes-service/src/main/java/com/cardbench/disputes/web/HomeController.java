package com.cardbench.disputes.web;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.cardbench.disputes.service.DirectoryService;

@Controller
public class HomeController {
    private final DirectoryService directory;

    public HomeController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String home(Model model) {
        model.addAttribute("cardmembers", directory.listCardmembers());
        return "home";
    }
}
