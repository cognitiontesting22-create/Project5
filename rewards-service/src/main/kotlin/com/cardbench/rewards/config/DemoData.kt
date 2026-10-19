package com.cardbench.rewards.config

import com.cardbench.rewards.domain.CardTransaction
import com.cardbench.rewards.domain.Cardmember
import com.cardbench.rewards.domain.MerchantCategory
import com.cardbench.rewards.domain.PaymentCard
import com.cardbench.rewards.domain.TransactionType
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class RewardsStore(
    val cardmembers: List<Cardmember>,
    val cards: List<PaymentCard>,
    val transactions: List<CardTransaction>,
) {
    private val cardmemberById = cardmembers.associateBy(Cardmember::id)
    private val cardById = cards.associateBy(PaymentCard::id)
    private val transactionById = transactions.associateBy(CardTransaction::id)

    fun cardmember(id: String): Cardmember? = cardmemberById[id]
    fun card(id: String): PaymentCard? = cardById[id]
    fun transaction(id: String): CardTransaction? = transactionById[id]
}

@Configuration
class DemoData {
    private val names = listOf(
        "Avery Morgan",
        "Jordan Lee",
        "Riley Chen",
        "Casey Patel",
        "Taylor Brooks",
        "Morgan Ellis",
    )
    private val merchants = listOf(
        "Northstar Market",
        "Juniper Cafe",
        "Harborline Transit",
        "Cedar & Stone",
        "Brightpath Books",
        "Cloudberry Outfitters",
        "Copper Kettle",
        "Lumen Home",
        "Fieldwork Supply",
        "Maple Street Cinema",
    )
    private val categories = listOf(
        MerchantCategory.GROCERIES,
        MerchantCategory.DINING,
        MerchantCategory.TRAVEL,
        MerchantCategory.DINING,
        MerchantCategory.OTHER,
        MerchantCategory.OTHER,
        MerchantCategory.DINING,
        MerchantCategory.OTHER,
        MerchantCategory.FUEL,
        MerchantCategory.OTHER,
    )

    @Bean
    fun rewardsStore(clock: Clock): RewardsStore {
        val cardmembers = names.mapIndexed { index, name ->
            val id = "member-%02d".format(index + 1)
            Cardmember(id, name, "$id@cardbench.example")
        }
        val cards = (0 until 10).map { index ->
            PaymentCard(
                id = "card-%02d".format(index + 1),
                label = "Everyday card %02d".format(index + 1),
                cardmemberId = cardmembers[index % cardmembers.size].id,
            )
        }
        val today = LocalDate.now(clock)
        val transactions = mutableListOf<CardTransaction>()
        for (index in 0 until 120) {
            val id = "reward-txn-%03d".format(index + 1)
            val card = cards[index % cards.size]
            val isRefund = index >= 10 && index % 10 == (index / 10 - 1) % 10
            val refundTarget = if (isRefund) {
                transactions.last { it.cardId == card.id && it.type == TransactionType.PURCHASE }
            } else {
                null
            }
            val cents = 735L + ((index + 1) * 1187L % 48265L)
            val amount = refundTarget?.amount ?: BigDecimal.valueOf(cents, 2)
            val offset = 199L - (index * 199L / 119L)
            transactions += CardTransaction(
                id = id,
                cardId = card.id,
                merchant = merchants[index % merchants.size],
                category = categories[index % categories.size],
                amount = amount,
                postingDate = today.minusDays(offset),
                type = if (isRefund) TransactionType.REFUND else TransactionType.PURCHASE,
                refundOf = refundTarget?.id,
            )
        }
        return RewardsStore(cardmembers, cards, transactions)
    }
}
