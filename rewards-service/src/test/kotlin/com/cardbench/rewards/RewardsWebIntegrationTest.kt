package com.cardbench.rewards

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import com.cardbench.rewards.config.RewardsStore
import com.cardbench.rewards.domain.TransactionType
import java.time.Clock
import java.time.LocalDate

@SpringBootTest(properties = ["cardbench.today=2026-01-15"])
@AutoConfigureMockMvc
class RewardsWebIntegrationTest {
    @Autowired
    private lateinit var mvc: MockMvc
    @Autowired
    private lateinit var store: RewardsStore
    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `seed has stable relationships and dates relative to the pinned clock`() {
        val today = LocalDate.now(clock)
        assertEquals(6, store.cardmembers.size)
        assertEquals(10, store.cards.size)
        assertEquals(120, store.transactions.size)
        assertEquals(11, store.transactions.count { it.type == TransactionType.REFUND })
        store.transactions.forEachIndexed { index, transaction ->
            assertEquals(today.minusDays(199L - index * 199L / 119L), transaction.postingDate)
            if (transaction.type == TransactionType.REFUND) {
                val purchase = store.transaction(transaction.refundOf!!)!!
                assertEquals(TransactionType.PURCHASE, purchase.type)
                assertEquals(transaction.cardId, purchase.cardId)
                assertTrue(store.transactions.indexOf(purchase) < index)
            }
        }
    }

    @Test
    fun `api lists deterministic members cards and transactions`() {
        mvc.perform(get("/api/cardmembers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].cards[0].balance").isNumber)
        mvc.perform(get("/api/cards"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(10))
        mvc.perform(get("/api/cards/card-01/transactions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(12))
            .andExpect(jsonPath("$[0].postingDate").value("2025-06-30"))
    }

    @Test
    fun `api exposes purchase ledger and current balance`() {
        mvc.perform(get("/api/cards/card-01/ledger"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(jsonPath("$[0].points").isNumber)
        mvc.perform(get("/api/cards/card-01/balance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cardId").value("card-01"))
            .andExpect(jsonPath("$.balance").isNumber)
    }

    @Test
    fun `api returns structured not found errors`() {
        mvc.perform(get("/api/cards/missing-card/balance"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Card not found: missing-card"))
    }

    @Test
    fun `server rendered home and card ledger pages work`() {
        mvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("home"))
            .andExpect(content().string(containsString("Avery Morgan")))
            .andExpect(content().string(containsString("pts")))
        mvc.perform(get("/cards/card-01"))
            .andExpect(status().isOk)
            .andExpect(view().name("card"))
            .andExpect(content().string(containsString("Full rewards ledger")))
            .andExpect(content().string(containsString("Northstar Market")))
    }
}
