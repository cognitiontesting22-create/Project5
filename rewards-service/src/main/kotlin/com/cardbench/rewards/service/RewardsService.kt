package com.cardbench.rewards.service

import com.cardbench.rewards.config.RewardsStore
import com.cardbench.rewards.domain.CardSummary
import com.cardbench.rewards.domain.CardTransaction
import com.cardbench.rewards.domain.LedgerEntry
import com.cardbench.rewards.domain.MemberDashboard
import com.cardbench.rewards.domain.TransactionType
import org.springframework.stereotype.Service

@Service
class RewardsService(private val store: RewardsStore) {
    fun cardmembers(): List<MemberDashboard> =
        store.cardmembers.map { member ->
            MemberDashboard(
                id = member.id,
                name = member.name,
                email = member.email,
                cards = store.cards.filter { it.cardmemberId == member.id }.map(::summary),
            )
        }

    fun cards(): List<CardSummary> = store.cards.map(::summary)

    fun transactions(cardId: String): List<CardTransaction> {
        requireCard(cardId)
        return store.transactions.filter { it.cardId == cardId }
    }

    fun ledger(cardId: String): List<LedgerEntry> =
        transactions(cardId)
            .asSequence()
            .filter { it.type == TransactionType.PURCHASE }
            .map { transaction ->
                LedgerEntry(
                    transactionId = transaction.id,
                    merchant = transaction.merchant,
                    category = transaction.category,
                    amount = transaction.amount,
                    points = transaction.amount.toLong(),
                    earnedOn = transaction.postingDate,
                )
            }
            .toList()

    fun balance(cardId: String): Long = ledger(cardId).sumOf(LedgerEntry::points)

    fun cardSummary(cardId: String): CardSummary = summary(requireCard(cardId))

    private fun summary(card: com.cardbench.rewards.domain.PaymentCard): CardSummary {
        val member = store.cardmember(card.cardmemberId)
            ?: throw IllegalStateException("Card ${card.id} has no cardmember")
        return CardSummary(
            id = card.id,
            label = card.label,
            cardmemberId = member.id,
            cardmemberName = member.name,
            balance = ledgerForExistingCard(card.id),
        )
    }

    private fun ledgerForExistingCard(cardId: String): Long =
        store.transactions.asSequence()
            .filter { it.cardId == cardId && it.type == TransactionType.PURCHASE }
            .sumOf { it.amount.toLong() }

    private fun requireCard(cardId: String) =
        store.card(cardId) ?: throw ResourceNotFoundException("Card not found: $cardId")
}
