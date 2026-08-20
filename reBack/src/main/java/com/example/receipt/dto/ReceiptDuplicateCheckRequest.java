package com.example.receipt.dto;

import java.util.List;

public record ReceiptDuplicateCheckRequest(List<String> lines) {
}
