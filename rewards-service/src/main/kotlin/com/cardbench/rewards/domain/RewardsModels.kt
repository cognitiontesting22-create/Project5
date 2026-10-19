package com.cardbench.rewards.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Cardmember(
    val id: String,
    val name: String,
    val email: String,
)

data class PaymentCard(
    val id: String,
    val label: String,
    val cardmemberId: String,
)

enum class MerchantCategory {
    DINING,
    TRAVEL,
    GROCERIES,
    FUEL,
    OTHER,
}

enum class TransactionType {
    PURCHASE,
    REFUND,
}

data class CardTransaction(
    val id: String,
    val cardId: String,
    val merchant: String,
    val category: MerchantCategory,
    val amount: BigDecimal,
    val postingDate: LocalDate,
    val type: TransactionType,
    val refundOf: String? = null,
)

data class LedgerEntry(
    val transactionId: String,
    val merchant: String,
    val category: MerchantCategory,
    val amount: BigDecimal,
    val points: Long,
    val earnedOn: LocalDate,
)

data class CardSummary(
    val id: String,
    val label: String,
    val cardmemberId: String,
    val cardmemberName: String,
    val balance: Long,
)

data class MemberDashboard(
    val id: String,
    val name: String,
    val email: String,
    val cards: List<CardSummary>,
)
