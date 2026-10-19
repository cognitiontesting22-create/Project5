package com.cardbench.disputes;

import static org.hamcrest.Matchers.containsString;
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

@SpringBootTest(properties = "cardbench.today=2026-01-15")
@AutoConfigureMockMvc
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
    }

    @Test
    void apiEnforcesLifecycleAndReturnsResolutionNote() throws Exception {
        String response = mvc.perform(post("/api/disputes")
                        .contentType("application/json")
                        .content("""
                                {"transactionId":"txn-001","amount":999.99,"reason":"Anything, even an arbitrary explanation"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(999.99))
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
                .andExpect(jsonPath("$.reason").value("Anything, even an arbitrary explanation"))
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
                        .content("{\"transactionId\":\"missing\",\"amount\":3.00,\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("/api/disputes/1/status")
                        .contentType("application/json").content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
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
                .andExpect(content().string(containsString("Northstar Market")));
        String detailUrl = mvc.perform(post("/cardmembers/member-01/disputes")
                        .param("transactionId", "txn-001")
                        .param("amount", "500.00")
                        .param("reason", "Filed from the member desk"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
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
                .andExpect(content().string(containsString("Withdraw")));
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
}
