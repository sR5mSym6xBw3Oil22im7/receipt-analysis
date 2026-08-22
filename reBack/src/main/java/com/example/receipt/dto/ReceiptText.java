package com.example.receipt.dto;

import java.util.List;

public record ReceiptText(List<String> lines, String sha256) {
    public ReceiptText(List<String> lines) {
        this(lines, null);
    }
}
