package com.cardbench.rewards.config

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClockConfiguration {
    @Bean
    fun applicationClock(@Value("\${cardbench.today:}") pinnedToday: String): Clock =
        if (pinnedToday.isBlank()) {
            Clock.systemUTC()
        } else {
            Clock.fixed(LocalDate.parse(pinnedToday).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        }
}
