package com.example.receipt.game;

import com.example.receipt.dto.ReceiptItemData;
import java.time.LocalDateTime;
import java.util.List;

public record GameReceipt(String tableName, String imageSha256, String storeName, String storeCategory, LocalDateTime purchasedAt, Long totalAmount, List<ReceiptItemData> items) {
    public List<ReceiptItemData> safeItems() { return items == null ? List.of() : items; }
}
