package com.cardbench.disputes;

import static org.hamcrest.Matchers.containsString;
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
    void apiEnforcesLifecycleAndReturnsResolutionNote() throws Exception {
        String response = mvc.perform(post("/api/disputes")
                        .contentType("application/json")
                        .content("""
                                {"transactionId":"txn-001","amount":19.22,"reasonCode":"NOT_RECEIVED","description":"The item never arrived"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(19.22))
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
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"AWAITING_CARDMEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CARDMEMBER"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json").content("{\"status\":\"RESOLVED_DENIED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/disputes/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED_DENIED\",\"resolutionNote\":\"Merchant provided proof\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_DENIED"))
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
                .andExpect(content().string(containsString("This dispute is final")));
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
        String request = "{\"transactionId\":\"txn-004\",\"amount\":54.83,\"reasonCode\":\"NOT_RECEIVED\"}";
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
                .andExpect(content().string(containsString("Fraud")));
        mvc.perform(post("/cardmembers/member-01/disputes")
                        .param("transactionId", "txn-041")
                        .param("amount", "1")
                        .param("reasonCode", "INVALID"))
                .andExpect(status().is3xxRedirection());
        String errorPage = mvc.perform(get("/cardmembers/member-01"))
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
