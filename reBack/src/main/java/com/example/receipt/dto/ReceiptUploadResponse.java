package com.example.receipt.dto;

import java.util.List;

public record ReceiptUploadResponse(
        String tableName,
        int lineCount,
        List<String> lines
) {
}
