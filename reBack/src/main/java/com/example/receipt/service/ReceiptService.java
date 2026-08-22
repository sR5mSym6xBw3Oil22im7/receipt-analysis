package com.example.receipt.service;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.dto.ReceiptUploadResponse;
import com.example.receipt.repository.ReceiptTableRepository;
import com.example.receipt.exception.ReceiptException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
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

    public ReceiptUploadResponse store(java.util.List<String> lines) {
        return store(lines, null);
    }

    @Transactional
    public ReceiptUploadResponse store(java.util.List<String> lines, String idempotencyKey) {
        // 保存するのはGemini/OCRが返した元のテキスト。比較用の正規化は指紋生成時だけ行う。
        java.util.List<String> originalLines = lines == null
                ? java.util.List.of()
                : lines.stream().filter(java.util.Objects::nonNull).toList();
        if (ReceiptFingerprint.normalizeLines(originalLines).isEmpty()) {
            throw new ReceiptException(HttpStatus.BAD_REQUEST, "EMPTY_RECEIPT", "レシートデータが空です。");
        }
        String canonicalText = ReceiptFingerprint.canonicalText(originalLines);
        String fingerprint = ReceiptFingerprint.sha256(canonicalText);
        repository.ensureFingerprintRegistry();
        repository.lockFingerprint(fingerprint);
        repository.backfillFingerprintRegistry();

        var existingRequest = repository.findByIdempotencyKey(idempotencyKey);
        if (existingRequest.isPresent()) {
            if (existingRequest.get().fingerprint().equals(fingerprint)) {
                return new ReceiptUploadResponse(existingRequest.get().tableName(), originalLines.size(), originalLines);
            }
            throw new ReceiptException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "異なるレシートに同じ冪等キーは使用できません。");
        }
        if (repository.findTableByFingerprint(fingerprint).isPresent()) {
            throw new ReceiptException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_RECEIPT",
                    "同じレシートデータがすでに登録されています。"
            );
        }
        String tableName;
        try {
            // 指紋を先に一意予約する。予約成功後の本体作成まで同一トランザクションで行う。
            tableName = repository.reserveFingerprint(fingerprint, canonicalText, idempotencyKey);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ReceiptException(HttpStatus.CONFLICT, "DUPLICATE_RECEIPT", "同じレシートデータがすでに登録されています。");
        }
        repository.createReceiptTableAndInsert(tableName, originalLines);
        return new ReceiptUploadResponse(tableName, originalLines.size(), originalLines);
    }
}
