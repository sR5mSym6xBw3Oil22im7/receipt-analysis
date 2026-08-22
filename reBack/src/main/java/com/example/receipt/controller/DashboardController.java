package com.example.receipt.controller;

import com.example.receipt.dto.DashboardResponse;
import com.example.receipt.repository.ReceiptAnalyticsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final ReceiptAnalyticsRepository repository;
    public DashboardController(ReceiptAnalyticsRepository repository) { this.repository = repository; }
    @GetMapping
    public DashboardResponse dashboard() { return repository.dashboard(); }
}
