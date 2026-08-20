package com.example.receipt.controller;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.service.ReceiptAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceiptControllerIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CapturingReceiptAnalyzer analyzer;

    @BeforeEach
    void resetAnalyzer() {
        analyzer.lastGeminiApiKey = null;
        jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name LIKE 'receipt_%'",
                String.class
        ).forEach(tableName -> jdbcTemplate.execute("DROP TABLE " + tableName));
    }

    @Test
    void uploadCallsAnalyzerAndCreatesReceiptTable() throws Exception {
        int before = receiptTableCount();
        MockMultipartFile file = sampleFile();

        mockMvc.perform(multipart("/api/receipts")
                        .file(file)
                        .param("geminiApiKey", "web-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value(org.hamcrest.Matchers.matchesPattern("receipt_[0-9a-f]{32}")))
                .andExpect(jsonPath("$.lineCount").value(3))
                .andExpect(jsonPath("$.lines[0]").value("SAMPLE STORE"));

        assertThat(receiptTableCount()).isEqualTo(before + 1);
        assertThat(analyzer.lastGeminiApiKey).isEqualTo("web-key");
    }

    @Test
    void uploadPassesWebApiKeyToAnalyzer() throws Exception {
        MockMultipartFile file = sampleFile();

        mockMvc.perform(multipart("/api/receipts")
                        .file(file)
                        .param("geminiApiKey", "web-key-2"))
                .andExpect(status().isOk());

        assertThat(analyzer.lastGeminiApiKey).isEqualTo("web-key-2");
    }

    @Test
    void uploadRequiresWebApiKey() throws Exception {
        MockMultipartFile file = sampleFile();

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile(
                "file",
                "sample.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );
    }

    private int receiptTableCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name LIKE 'receipt_%'",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class StubAnalyzerConfiguration {
        @Bean
        @Primary
        CapturingReceiptAnalyzer stubReceiptAnalyzer() {
            return new CapturingReceiptAnalyzer();
        }
    }

    static class CapturingReceiptAnalyzer implements ReceiptAnalyzer {
        volatile String lastGeminiApiKey;

        @Override
        public ReceiptText analyze(byte[] bytes, String mimeType, String geminiApiKey) {
            lastGeminiApiKey = geminiApiKey;
            return new ReceiptText(List.of(
                    "SAMPLE STORE",
                    "ITEM 100",
                    "TOTAL 100"
            ));
        }
    }
}
