package com.cardbench.rewards.service

import com.cardbench.rewards.config.RewardsStore
import com.cardbench.rewards.domain.CardTransaction
import com.cardbench.rewards.domain.Cardmember
import com.cardbench.rewards.domain.MerchantCategory
import com.cardbench.rewards.domain.PaymentCard
import com.cardbench.rewards.domain.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RewardsServiceTest {
    private val member = Cardmember("member-01", "Avery Morgan", "member-01@cardbench.example")
    private val card = PaymentCard("card-01", "Everyday card 01", member.id)
    private val purchase = CardTransaction(
        id = "txn-001",
        cardId = card.id,
        merchant = "Juniper Cafe",
        category = MerchantCategory.DINING,
        amount = BigDecimal("19.99"),
        postingDate = LocalDate.parse("2026-01-15"),
        type = TransactionType.PURCHASE,
    )
    private val refund = CardTransaction(
        id = "txn-002",
        cardId = card.id,
        merchant = purchase.merchant,
        category = purchase.category,
        amount = purchase.amount,
        postingDate = LocalDate.parse("2026-01-16"),
        type = TransactionType.REFUND,
        refundOf = purchase.id,
    )
    private val service = RewardsService(RewardsStore(listOf(member), listOf(card), listOf(purchase, refund)))

    @Test
    fun `ledger earns one point per whole purchase dollar and ignores refunds`() {
        assertEquals(1, service.ledger(card.id).size)
        assertEquals(19L, service.ledger(card.id).single().points)
        assertEquals(19L, service.balance(card.id))
    }

    @Test
    fun `cardmember view includes its card balance`() {
        val dashboard = service.cardmembers().single()

        assertEquals(member.id, dashboard.id)
        assertEquals(19L, dashboard.cards.single().balance)
    }

    @Test
    fun `transactions include refunds while ledger does not`() {
        assertEquals(2, service.transactions(card.id).size)
        assertEquals(TransactionType.REFUND, service.transactions(card.id).last().type)
    }

    @Test
    fun `unknown card is reported`() {
        assertThrows(ResourceNotFoundException::class.java) { service.balance("missing-card") }
    }
}
