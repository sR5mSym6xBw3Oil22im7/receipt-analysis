package com.example.receipt.dto;

import java.util.List;

public record ReceiptSaveRequest(List<String> lines, String sha256) {
    public ReceiptSaveRequest(List<String> lines) {
        this(lines, null);
    }
}
