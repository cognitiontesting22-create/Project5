package com.cardbench.disputes.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {
    @Bean
    Clock applicationClock(@Value("${cardbench.today:}") String pinnedToday) {
        if (pinnedToday == null || pinnedToday.isBlank()) {
            return Clock.systemUTC();
        }
        return Clock.fixed(LocalDate.parse(pinnedToday).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }
}
