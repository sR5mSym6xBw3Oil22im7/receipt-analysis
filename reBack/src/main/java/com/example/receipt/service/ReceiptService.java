package com.example.receipt.service;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.dto.ReceiptUploadResponse;
import com.example.receipt.exception.ReceiptException;
import com.example.receipt.repository.ReceiptTableRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ReceiptService {
    private final ReceiptAnalyzer receiptAnalyzer;
    private final ReceiptUploadValidator validator;
    private final ReceiptTableRepository repository;

    public ReceiptService(
            ReceiptAnalyzer receiptAnalyzer,
            ReceiptUploadValidator validator,
            ReceiptTableRepository repository) {
        this.receiptAnalyzer = receiptAnalyzer;
        this.validator = validator;
        this.repository = repository;
    }

    public ReceiptText analyze(MultipartFile file, String geminiApiKey) throws IOException {
        validator.validate(file);
        return receiptAnalyzer.analyze(
                file.getBytes(),
                file.getContentType(),
                geminiApiKey
        );
    }

    @Transactional
    public ReceiptUploadResponse store(java.util.List<String> lines) {
        java.util.List<String> originalLines = lines == null
                ? java.util.List.of()
                : lines.stream().filter(java.util.Objects::nonNull).toList();
        if (originalLines.isEmpty()) {
            throw new ReceiptException(HttpStatus.BAD_REQUEST, "EMPTY_RECEIPT", "レシートデータが空です。");
        }
        String tableName = repository.createReceiptTableAndInsert(originalLines);
        return new ReceiptUploadResponse(tableName, originalLines.size(), originalLines);
    }
}
