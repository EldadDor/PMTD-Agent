package dev.pmtd.agent.persistence

import com.zaxxer.hikari.HikariDataSource
import dev.pmtd.agent.MovieTvAgentApplication
import dev.pmtd.agent.catalog.CatalogRepository
import dev.pmtd.agent.imdb.ImdbHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.simple.JdbcClient

class PostgresProfileTest {
    @Test
    fun `postgres profile wires JDBC with external settings without connecting`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MovieTvAgentApplication::class.java)
            .withPropertyValues(
                "spring.profiles.active=postgres",
                "spring.flyway.enabled=false",
                "PMTD_DB_HOST=127.0.0.1",
                "PMTD_DB_PORT=1",
                "PMTD_DB_NAME=fixture_database",
                "PMTD_DB_USERNAME=fixture_user",
                "PMTD_DB_PASSWORD=fixture_password",
                "PMTD_DB_SCHEMA=pmtd_fixture",
                "PMTD_DB_SSLMODE=verify-full",
            ).run { context ->
                assertThat(context).hasNotFailed()
                    .hasSingleBean(JdbcClient::class.java)
                    .hasSingleBean(CatalogRepository::class.java)
                    .hasSingleBean(ImdbHistoryRepository::class.java)
                val dataSource = context.getBean(HikariDataSource::class.java)
                assertThat(dataSource.jdbcUrl).isEqualTo("jdbc:postgresql://127.0.0.1:1/fixture_database")
                assertThat(dataSource.schema).isEqualTo("pmtd_fixture")
                assertThat(dataSource.dataSourceProperties.getProperty("sslmode")).isEqualTo("verify-full")
                assertThat(dataSource.isRunning).isFalse()
                assertThat(context.environment.getProperty("spring.flyway.create-schemas")).isEqualTo("false")
                assertThat(context.environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true")
            }
    }
}
