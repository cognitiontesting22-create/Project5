package com.cardbench.disputes;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "cardbench.today=2026-01-15")
@AutoConfigureMockMvc
@Transactional
class DisputesWebIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void apiListsTheDeterministicSeedDataAndUsesThePinnedDate() throws Exception {
        mvc.perform(get("/api/cardmembers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].id").value("member-01"));
        mvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(80))
                .andExpect(jsonPath("$[0].postingDate").value("2025-12-09"));
        mvc.perform(get("/api/transactions").param("cardmemberId", "member-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));
        mvc.perform(get("/api/dispute-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("FRAUD"))
                .andExpect(jsonPath("$[0].label").value("Fraud"));
    }

    @Test
    void queueApiCombinesFiltersSortsPagesAndRejectsUnknownValues() throws Exception {
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-041\",\"amount\":2,\"reasonCode\":\"FRAUD\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-041\",\"amount\":2,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondResponse = mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-051\",\"amount\":10,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(secondResponse).get("id").asLong();
        mvc.perform(put("/api/disputes/{id}/status", secondId).contentType("application/json")
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/disputes/queue")
                        .param("status", "OPEN", "UNDER_REVIEW")
                        .param("cardmemberId", "member-01")
                        .param("filedFrom", "2026-01-15")
                        .param("filedTo", "2026-01-15")
                        .param("sort", "amount")
                        .param("direction", "desc")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].dispute.amount").value(10))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-02-14"))
                .andExpect(jsonPath("$.items[0].overdue").value(false))
                .andExpect(jsonPath("$.summary.statusCounts.OPEN").value(1))
                .andExpect(jsonPath("$.summary.statusCounts.UNDER_REVIEW").value(1))
                .andExpect(jsonPath("$.summary.overdueCount").value(0));

        mvc.perform(get("/api/disputes/queue").param("sort", "merchant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/disputes/queue").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/analyst/disputes")
                        .param("cardmemberId", "")
                        .param("overdue", "")
                        .param("reasonCode", "")
                        .param("status", "OPEN", "UNDER_REVIEW")
                        .param("filedFrom", "2026-01-15")
                        .param("filedTo", "2026-01-15")
                        .param("sort", "amount")
                        .param("direction", "desc")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("name=\"status\" multiple")));
    }

    @Test
    void apiEnforcesLifecycleAndReturnsResolutionNote() throws Exception {
        String response = mvc.perform(post("/api/disputes")
                        .contentType("application/json")
                        .content("""
                                {"transactionId":"txn-001","amount":19.22,"reasonCode":"NOT_RECEIVED","description":"The item never arrived"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(19.22))
                .andExpect(jsonPath("$.creditState").value("NONE"))
                .andReturn().getResponse().getContentAsString();

        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asLong();

        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED_APPROVED\",\"resolutionNote\":\"Refund issued\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(get("/api/disputes/{id}", id))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/disputes/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("FILED"))
                .andExpect(jsonPath("$[0].actor").value("system"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.creditState").value("PROVISIONAL"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"AWAITING_CARDMEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CARDMEMBER"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.creditState").value("PROVISIONAL"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"RESOLVED_DENIED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED_DENIED\",\"resolutionNote\":\"Merchant provided proof\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_DENIED"))
                .andExpect(jsonPath("$.creditState").value("REVERSED"))
                .andExpect(jsonPath("$.resolutionNote").value("Merchant provided proof"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(get("/api/disputes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("NOT_RECEIVED"))
                .andExpect(jsonPath("$.description").value("The item never arrived"))
                .andExpect(jsonPath("$.resolutionNote").value("Merchant provided proof"));
        mvc.perform(get("/analyst/disputes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Resolved in merchant")))
                .andExpect(content().string(containsString("Merchant provided proof")))
                .andExpect(content().string(containsString("reversed")))
                .andExpect(content().string(containsString("This dispute is final")));
        mvc.perform(get("/api/cards/card-01/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsInForceTotal").value(0.00))
                .andExpect(jsonPath("$.adjustments.length()").value(2))
                .andExpect(jsonPath("$.adjustments[0].kind").value("PROVISIONAL_CREDIT"))
                .andExpect(jsonPath("$.adjustments[1].kind").value("REVERSAL"));
    }

    @Test
    void historyIsAppendOnlyActorAwareAndPrivateNotesAreHiddenFromCardmembers() throws Exception {
        String response = mvc.perform(post("/api/disputes").header("X-Actor", "Morgan Analyst")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"txn-041\",\"amount\":1.00,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asLong();

        mvc.perform(put("/api/disputes/{id}/status", id).header("X-Actor", "Casey Analyst")
                        .contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/disputes/{id}/status", id).header("X-Actor", "Casey Analyst")
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED_DENIED\",\"resolutionNote\":\"Evidence reviewed\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/disputes/{id}/notes", id).header("X-Actor", "Jordan Analyst")
                        .contentType("application/json")
                        .content("{\"text\":\"Private investigation detail\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("Jordan Analyst"))
                .andExpect(jsonPath("$.internal").value(true));
        mvc.perform(post("/api/disputes/{id}/notes", id).header("X-Actor", "Riley Analyst")
                        .contentType("application/json")
                        .content("{\"text\":\"Customer update\",\"internal\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/disputes/{id}/notes", id).header("X-Actor", "Riley Analyst")
                        .contentType("application/json").content("{\"text\":\"  \",\"internal\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        mvc.perform(post("/api/disputes/{id}/notes", id).contentType("application/json")
                        .content("{\"text\":\"" + "x".repeat(1001) + "\",\"internal\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOTE_TOO_LONG"));

        mvc.perform(get("/api/disputes/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].kind").value("FILED"))
                .andExpect(jsonPath("$[0].actor").value("Morgan Analyst"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-01-15T00:00:00Z"))
                .andExpect(jsonPath("$[1].kind").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].text").value("Status changed from OPEN to UNDER_REVIEW."))
                .andExpect(jsonPath("$[2].kind").value("CREDIT_PLACED"))
                .andExpect(jsonPath("$[3].kind").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[4].kind").value("NOTE"))
                .andExpect(jsonPath("$[5].kind").value("CREDIT_REVERSED"))
                .andExpect(jsonPath("$[6].internal").value(true))
                .andExpect(jsonPath("$[7].text").value("Customer update"));

        String analystPage = mvc.perform(get("/analyst/disputes/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(analystPage.contains("Private investigation detail"));
        assertTrue(analystPage.contains("Jordan Analyst"));

        String cardmemberPage = mvc.perform(get("/cardmembers/member-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cardmemberPage.contains("Customer update"));
        assertFalse(cardmemberPage.contains("Private investigation detail"));
        assertFalse(cardmemberPage.contains("Morgan Analyst"));
        assertFalse(cardmemberPage.contains("Jordan Analyst"));
    }

    @Test
    void cardAccountTracksCreditsAndReversalsWithExactAmounts() throws Exception {
        String initialAccount = mvc.perform(get("/api/cards/card-01/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postedTransactionsTotal").isNumber())
                .andExpect(jsonPath("$.creditsInForceTotal").value(0.00))
                .andExpect(jsonPath("$.adjustments.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        var initialAccountJson = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(initialAccount);
        assertEquals(initialAccountJson.get("postedTransactionsTotal").decimalValue(),
                initialAccountJson.get("balanceOwed").decimalValue());

        String response = mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-001\",\"amount\":19.22,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asLong();

        mvc.perform(put("/api/disputes/{id}/status", id).contentType("application/json")
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditState").value("PROVISIONAL"));
        String provisionalAccount = mvc.perform(get("/api/cards/card-01/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsInForceTotal").value(19.22))
                .andExpect(jsonPath("$.adjustments.length()").value(1))
                .andExpect(jsonPath("$.adjustments[0].disputeId").value(id))
                .andExpect(jsonPath("$.adjustments[0].kind").value("PROVISIONAL_CREDIT"))
                .andExpect(jsonPath("$.adjustments[0].amount").value(19.22))
                .andExpect(jsonPath("$.adjustments[0].date").value("2026-01-15"))
                .andReturn().getResponse().getContentAsString();
        var provisionalAccountJson = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(provisionalAccount);
        assertEquals(new java.math.BigDecimal("19.22"),
                initialAccountJson.get("postedTransactionsTotal").decimalValue()
                        .subtract(provisionalAccountJson.get("balanceOwed").decimalValue()));

        mvc.perform(put("/api/disputes/{id}/status", id).contentType("application/json")
                        .content("{\"status\":\"AWAITING_CARDMEMBER\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/disputes/{id}/status", id).contentType("application/json")
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/disputes/{id}/status", id).contentType("application/json")
                        .content("{\"status\":\"RESOLVED_APPROVED\",\"resolutionNote\":\"Merchant confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditState").value("PERMANENT"));
        mvc.perform(get("/api/cards/card-01/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsInForceTotal").value(19.22))
                .andExpect(jsonPath("$.adjustments.length()").value(1))
                .andExpect(jsonPath("$.adjustments[0].kind").value("PERMANENT_CREDIT"));
    }

    @Test
    void apiReturnsStructuredErrors() throws Exception {
        mvc.perform(get("/api/disputes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/disputes")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"missing\",\"amount\":3.00,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("/api/disputes/1/status")
                        .contentType("application/json").content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void apiRejectsInvalidFilingsAndDoesNotStoreThem() throws Exception {
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":0,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_MUST_BE_POSITIVE"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":42.97,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_EXCEEDS_TRANSACTION"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":1,\"reasonCode\":\"UNRECOGNIZED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REASON_CODE"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":1.001,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_PRECISION_INVALID"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\",\"description\":\""
                                + "x".repeat(501) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DESCRIPTION_TOO_LONG"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-003\",\"amount\":1,\"reasonCode\":\"FRAUD\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FRAUD_MUST_DISPUTE_FULL_AMOUNT"));
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-005\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILING_WINDOW_EXPIRED"));
        mvc.perform(get("/api/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void apiRejectsASecondActiveDisputeWithConflict() throws Exception {
        String request = "{\"transactionId\":\"txn-002\",\"amount\":31.09,\"reasonCode\":\"NOT_RECEIVED\"}";
        mvc.perform(post("/api/disputes").contentType("application/json").content(request))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/disputes").contentType("application/json").content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_DISPUTE_EXISTS"));
        mvc.perform(get("/api/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deniedAndWithdrawnDisputesAllowRefilingButApprovalIsPermanent() throws Exception {
        String firstResponse = mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-006\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(firstResponse).get("id").asLong();
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-006\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/disputes/{id}/status", firstId).contentType("application/json")
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/disputes/{id}/status", firstId).contentType("application/json")
                        .content("{\"status\":\"RESOLVED_DENIED\",\"resolutionNote\":\"Merchant evidence\"}"))
                .andExpect(status().isOk());

        String secondResponse = mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-006\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(secondResponse).get("id").asLong();
        mvc.perform(put("/api/disputes/{id}/status", secondId).contentType("application/json")
                        .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-006\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated());

        String thirdResponse = mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-007\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long thirdId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(thirdResponse).get("id").asLong();
        mvc.perform(put("/api/disputes/{id}/status", thirdId).contentType("application/json")
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/disputes/{id}/status", thirdId).contentType("application/json")
                        .content("{\"status\":\"RESOLVED_APPROVED\",\"resolutionNote\":\"Refund issued\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/disputes").contentType("application/json")
                        .content("{\"transactionId\":\"txn-007\",\"amount\":1,\"reasonCode\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSACTION_ALREADY_APPROVED"));
    }

    @Test
    void serverRenderedCardmemberAndAnalystActionsWork() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("Avery Morgan")));
        mvc.perform(get("/cardmembers/member-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("cardmember"))
                .andExpect(content().string(containsString("Northstar Market")))
                .andExpect(content().string(containsString("Choose a reason")))
                .andExpect(content().string(containsString("Fraud")))
                .andExpect(content().string(containsString("/cardmembers/member-01/cards/card-01")));
        mvc.perform(get("/cardmembers/member-01/cards/card-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-account"))
                .andExpect(content().string(containsString("Posted transactions")))
                .andExpect(content().string(containsString("No dispute adjustments.")));
        var invalidFiling = mvc.perform(post("/cardmembers/member-01/disputes")
                        .param("transactionId", "txn-041")
                        .param("amount", "1")
                        .param("reasonCode", "INVALID"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) invalidFiling.getRequest().getSession(false);
        String errorPage = mvc.perform(get("/cardmembers/member-01").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(transactionRow(errorPage, "txn-041").contains("Choose one of the supported dispute reasons."));
        String oldPage = mvc.perform(get("/cardmembers/member-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(transactionRow(oldPage, "txn-021").contains("<summary>File dispute</summary>"));
        assertTrue(transactionRow(oldPage, "txn-021").contains("outside the 120-day filing window"));

        String detailUrl = mvc.perform(post("/cardmembers/member-01/disputes")
                        .param("transactionId", "txn-041")
                        .param("amount", "11.37")
                        .param("reasonCode", "NOT_RECEIVED")
                        .param("description", "Filed from the member desk"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String activePage = mvc.perform(get("/cardmembers/member-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(transactionRow(activePage, "txn-041").contains("<summary>File dispute</summary>"));
        assertTrue(transactionRow(activePage, "txn-041").contains("already has an active dispute"));
        String disputeId = detailUrl.substring(detailUrl.lastIndexOf('/') + 1);
        mvc.perform(get("/analyst/disputes"))
                .andExpect(status().isOk())
                .andExpect(view().name("disputes"));
        mvc.perform(get(detailUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("dispute"))
                .andExpect(content().string(containsString("Filed from the member desk")))
                .andExpect(content().string(containsString("Under review")))
                .andExpect(content().string(containsString("Withdrawn")));
        mvc.perform(get("/cardmembers/member-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your disputes")))
                .andExpect(content().string(containsString("Withdraw")))
                .andExpect(content().string(containsString("outside the 120-day filing window")));
        mvc.perform(post(detailUrl + "/status").param("status", "UNDER_REVIEW"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(detailUrl))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Awaiting cardmember")))
                .andExpect(content().string(containsString("Resolution note")));
        mvc.perform(post("/cardmembers/member-01/disputes/{id}/withdraw", disputeId))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/cardmembers/member-01"))
                .andExpect(content().string(containsString("Withdrawn")));
    }

    private String transactionRow(String html, String transactionId) {
        int start = html.indexOf(transactionId);
        assertTrue(start >= 0, "Transaction row should be present: " + transactionId);
        int end = html.indexOf("</tr>", start);
        assertTrue(end >= 0, "Transaction row should be closed: " + transactionId);
        return html.substring(start, end);
    }
}
