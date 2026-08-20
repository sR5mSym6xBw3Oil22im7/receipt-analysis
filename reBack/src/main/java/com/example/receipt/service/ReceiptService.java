package com.example.receipt.service;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.dto.ReceiptUploadResponse;
import com.example.receipt.repository.ReceiptTableRepository;
import com.example.receipt.exception.ReceiptException;
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

    public boolean isDuplicate(java.util.List<String> lines) {
        return repository.receiptExists(lines);
    }

    @Transactional
    public ReceiptUploadResponse store(java.util.List<String> lines) {
        if (repository.receiptExists(lines)) {
            throw new ReceiptException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_RECEIPT",
                    "同じレシートデータがすでに登録されています。"
            );
        }
        String tableName = repository.createReceiptTableAndInsert(lines);
        return new ReceiptUploadResponse(tableName, lines.size(), lines);
    }
}
