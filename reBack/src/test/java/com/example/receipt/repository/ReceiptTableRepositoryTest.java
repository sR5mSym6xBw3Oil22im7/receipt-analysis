package com.example.receipt.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(ReceiptTableRepository.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:repo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ReceiptTableRepositoryTest {
    @Autowired
    ReceiptTableRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsOneIndependentTablePerReceiptAndInsertsLines() {
        String first = repository.createReceiptTableAndInsert(List.of("STORE A", "TOTAL 100"));
        String second = repository.createReceiptTableAndInsert(List.of("STORE B"));

        assertThat(first).matches("receipt_[0-9a-f]{32}");
        assertThat(second).matches("receipt_[0-9a-f]{32}");
        assertThat(second).isNotEqualTo(first);

        Integer firstCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + first, Integer.class);
        Integer secondCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + second, Integer.class);
        String firstLine = jdbcTemplate.queryForObject(
                "SELECT text FROM " + first + " WHERE line_no = 1",
                String.class
        );

        assertThat(firstCount).isEqualTo(2);
        assertThat(secondCount).isEqualTo(1);
        assertThat(firstLine).isEqualTo("STORE A");
    }
}
