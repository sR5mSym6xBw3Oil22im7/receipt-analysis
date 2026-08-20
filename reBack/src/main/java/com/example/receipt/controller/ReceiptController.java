package com.example.receipt.controller;

import com.example.receipt.dto.ReceiptUploadResponse;
import com.example.receipt.dto.ReceiptSaveRequest;
import com.example.receipt.dto.ReceiptText;
import com.example.receipt.service.ReceiptService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("geminiApiKey") String geminiApiKey) throws IOException {
        return ResponseEntity.ok(receiptService.analyzeAndStore(file, geminiApiKey));
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptText> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("geminiApiKey") String geminiApiKey) throws IOException {
        return ResponseEntity.ok(receiptService.analyze(file, geminiApiKey));
    }

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReceiptUploadResponse> save(@RequestBody ReceiptSaveRequest request) {
        return ResponseEntity.ok(receiptService.store(request.lines()));
    }
}
