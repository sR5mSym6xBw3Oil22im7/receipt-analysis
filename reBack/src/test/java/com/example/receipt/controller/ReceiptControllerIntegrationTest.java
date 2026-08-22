package com.example.receipt.controller;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.service.ReceiptAnalyzer;
import com.example.receipt.repository.ReceiptTableName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void legacyRootUploadEndpointCannotCreateReceiptTable() throws Exception {
        int before = receiptTableCount();

        mockMvc.perform(multipart("/api/receipts")
                        .file(sampleFile())
                        .param("geminiApiKey", "web-key"))
                .andExpect(status().isMethodNotAllowed());

        assertThat(receiptTableCount()).isEqualTo(before);
        assertThat(analyzer.lastGeminiApiKey).isNull();
    }

    @Test
    void analyzeDoesNotCreateReceiptTable() throws Exception {
        int before = receiptTableCount();

        mockMvc.perform(multipart("/api/receipts/analyze")
                        .file(sampleFile())
                        .param("geminiApiKey", "web-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0]").value("SAMPLE STORE"));

        assertThat(receiptTableCount()).isEqualTo(before);
    }

    @Test
    void saveRejectsDuplicateWithoutCreatingSecondTable() throws Exception {
        String requestBody = """
                {"lines":["SAMPLE STORE","ITEM 100","TOTAL 100"]}
                """;
        int before = receiptTableCount();

        mockMvc.perform(post("/api/receipts/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(3));

        mockMvc.perform(post("/api/receipts/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT"));

        assertThat(receiptTableCount()).isEqualTo(before + 1);
    }

    @Test
    void normalizedUtf8TextIsProtectedByFingerprintAndIdempotencyIsReplaySafe() throws Exception {
        String requestBody = "{\"lines\":[\"ＡＢＣ STORE\",\"TOTAL\\u00a0100\"]}";

                mockMvc.perform(post("/api/receipts/save")
                        .header("Idempotency-Key", "receipt-retry-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(2))
                .andExpect(jsonPath("$.lines[0]").value("ＡＢＣ STORE"))
                .andExpect(jsonPath("$.lines[1]").value("TOTAL 100"));

        mockMvc.perform(post("/api/receipts/save")
                        .header("Idempotency-Key", "receipt-retry-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[\"ABCSTORE\",\"TOTAL100\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/receipts/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[\"ABC\\nSTORE\",\"TOTAL 100\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT"));
    }

    @Test
    void duplicateCheckIgnoresNonReceiptManagementTablesAndReturnsDuplicate() throws Exception {
        String requestBody = """
                {"lines":["SAMPLE STORE","ITEM 100","TOTAL 100"]}
                """;

        mockMvc.perform(post("/api/receipts/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        jdbcTemplate.execute("CREATE TABLE receipt_analysis_drafts (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE receipt_uniqueness_registry (id BIGINT PRIMARY KEY)");

        mockMvc.perform(post("/api/receipts/check-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(receiptTableCount()).isEqualTo(1);
    }

    @Test
    void analyzePassesWebApiKeyToAnalyzer() throws Exception {
        MockMultipartFile file = sampleFile();

        mockMvc.perform(multipart("/api/receipts/analyze")
                        .file(file)
                        .param("geminiApiKey", "web-key-2"))
                .andExpect(status().isOk());

        assertThat(analyzer.lastGeminiApiKey).isEqualTo("web-key-2");
    }

    @Test
    void analyzeRequiresWebApiKey() throws Exception {
        MockMultipartFile file = sampleFile();

        mockMvc.perform(multipart("/api/receipts/analyze").file(file))
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
        return (int) jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) LIKE 'receipt_%'",
                String.class
        ).stream().filter(ReceiptTableName::isSafe).count();
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
