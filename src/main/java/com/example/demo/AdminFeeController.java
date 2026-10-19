package com.example.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Admin side of the Health Services Fee (secured by /admin/**). Admins can no
// longer change the fee directly - they propose a change, which a fee approver
// must approve (see FeeSettingsService).
@RestController
@RequestMapping("/admin/fees")
public class AdminFeeController {

    private final FeeSettingsService feeSettingsService;

    public AdminFeeController(FeeSettingsService feeSettingsService) {
        this.feeSettingsService = feeSettingsService;
    }

    public static class ProposalRequest {
        public Double localFee;
        public Double regionalFee;
        public String reason;
    }

    @GetMapping
    public FeeSettings getFees() {
        return feeSettingsService.current();
    }

    @GetMapping("/proposals")
    public Map<String, Object> getProposals() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", feeSettingsService.pending());
        result.put("history", feeSettingsService.history());
        result.put("approverExists", feeSettingsService.approverExists());
        return result;
    }

    @PostMapping("/proposals")
    public FeeChangeRequest propose(@RequestBody ProposalRequest request, Authentication auth) {
        return feeSettingsService.propose(request.localFee, request.regionalFee, request.reason, auth);
    }

    @PostMapping("/proposals/{id}/withdraw")
    public FeeChangeRequest withdraw(@PathVariable Long id, Authentication auth) {
        return feeSettingsService.withdraw(id, auth);
    }
}
