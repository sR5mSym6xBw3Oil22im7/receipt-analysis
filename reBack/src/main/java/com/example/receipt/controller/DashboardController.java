package com.example.receipt.controller;

import com.example.receipt.dto.DashboardResponse;
import com.example.receipt.dto.DashboardAnalyzeRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final com.example.receipt.service.DashboardGeminiService service;
    public DashboardController(com.example.receipt.service.DashboardGeminiService service) { this.service = service; }

    @PostMapping("/analyze")
    public DashboardResponse analyze(@RequestBody DashboardAnalyzeRequest request) { return service.analyze(request.geminiApiKey()); }
}
