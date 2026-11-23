package dev.pmtd.agent.notification

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager

@Configuration
@Profile("line-webhook & postgres")
internal class LineWebhookConfiguration {
    @Bean
    fun lineFeedbackHandler(jdbc: JdbcClient, transactions: PlatformTransactionManager): LineFeedbackHandler =
        LineFeedbackRepository(jdbc, transactions)
}
