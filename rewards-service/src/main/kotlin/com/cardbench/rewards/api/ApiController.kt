package com.cardbench.rewards.api

import com.cardbench.rewards.domain.CardSummary
import com.cardbench.rewards.domain.CardTransaction
import com.cardbench.rewards.domain.LedgerEntry
import com.cardbench.rewards.domain.MemberDashboard
import com.cardbench.rewards.service.RewardsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApiController(private val rewards: RewardsService) {
    @GetMapping("/cardmembers")
    fun cardmembers(): List<MemberDashboard> = rewards.cardmembers()

    @GetMapping("/cards")
    fun cards(): List<CardSummary> = rewards.cards()

    @GetMapping("/cards/{cardId}/transactions")
    fun transactions(@PathVariable cardId: String): List<CardTransaction> = rewards.transactions(cardId)

    @GetMapping("/cards/{cardId}/ledger")
    fun ledger(@PathVariable cardId: String): List<LedgerEntry> = rewards.ledger(cardId)

    @GetMapping("/cards/{cardId}/balance")
    fun balance(@PathVariable cardId: String) = BalanceView(cardId, rewards.balance(cardId))

    data class BalanceView(val cardId: String, val balance: Long)
}
