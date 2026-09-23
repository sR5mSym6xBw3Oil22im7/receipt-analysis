package com.example.receipt.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {
    @Bean
    Flyway flyway(DataSource dataSource,
                  @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
                  @Value("${spring.flyway.baseline-version:0}") String baselineVersion) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load();
    }

    @Bean
    InitializingBean migrateFlyway(Flyway flyway) {
        return flyway::migrate;
    }
}
