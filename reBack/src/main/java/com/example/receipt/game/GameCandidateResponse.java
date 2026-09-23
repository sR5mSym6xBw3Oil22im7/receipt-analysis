package com.example.receipt.game;

import java.time.LocalDateTime;

public record GameCandidateResponse(String receiptTableName, String imageSha256, String storeName, String storeCategory, LocalDateTime purchasedAt, Long totalAmount, int itemCount) { }
