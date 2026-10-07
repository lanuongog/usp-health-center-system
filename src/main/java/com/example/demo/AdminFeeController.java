package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Admin view/edit of the Health Services Fee amounts (secured by /admin/**).
@RestController
@RequestMapping("/admin/fees")
public class AdminFeeController {

    private final FeeSettingsService feeSettingsService;

    public AdminFeeController(FeeSettingsService feeSettingsService) {
        this.feeSettingsService = feeSettingsService;
    }

    public static class FeeRequest {
        public Double localFee;
        public Double regionalFee;
    }

    @GetMapping
    public FeeSettings getFees() {
        return feeSettingsService.current();
    }

    @PutMapping
    public FeeSettings updateFees(@RequestBody FeeRequest request, Authentication auth) {
        return feeSettingsService.update(request.localFee, request.regionalFee, auth);
    }
}
