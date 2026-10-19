package com.cardbench.disputes.config;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.repository.DisputeEventRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Component
@Order(1)
public class DisputeHistoryInitializer implements CommandLineRunner {
    private final DisputeRepository disputes;
    private final DisputeEventRepository events;
    private final Clock clock;

    public DisputeHistoryInitializer(
            DisputeRepository disputes, DisputeEventRepository events, Clock clock) {
        this.disputes = disputes;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (Dispute dispute : disputes.findAll()) {
            if (!events.existsByDispute_IdAndKind(dispute.getId(), DisputeEventKind.FILED)) {
                events.save(new DisputeEvent(dispute, Instant.now(clock), DisputeEventKind.FILED,
                        "system", "Dispute filing recorded from seeded case data.", false));
            }
        }
    }
}
