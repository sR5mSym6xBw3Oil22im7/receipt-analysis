package com.example.receipt.dto;

import java.util.List;

public record ReceiptSaveRequest(List<String> lines) {
}
