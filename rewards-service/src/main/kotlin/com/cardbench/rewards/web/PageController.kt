package com.cardbench.rewards.web

import com.cardbench.rewards.service.RewardsService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class PageController(private val rewards: RewardsService) {
    @GetMapping("/")
    fun home(model: Model): String {
        model.addAttribute("members", rewards.cardmembers())
        return "home"
    }

    @GetMapping("/cards/{cardId}")
    fun card(@PathVariable cardId: String, model: Model): String {
        model.addAttribute("card", rewards.cardSummary(cardId))
        model.addAttribute("transactions", rewards.transactions(cardId))
        model.addAttribute("ledger", rewards.ledger(cardId))
        return "card"
    }
}
