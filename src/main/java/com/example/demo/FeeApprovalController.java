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

// The fee approver's side of the two-person rule (FEE_APPROVER only, see
// SecurityConfig): review the waiting fee change and approve or reject it.
// Approver ("Finance") accounts are created by admins on the Accounts page, or
// by the developers (app.fee-approver.*) - never from here. Any Finance
// account can decide; who created which account is in the audit log.
@RestController
@RequestMapping("/fee-approvals")
public class FeeApprovalController {

    private final FeeSettingsService feeSettingsService;

    public FeeApprovalController(FeeSettingsService feeSettingsService) {
        this.feeSettingsService = feeSettingsService;
    }

    @GetMapping
    public Map<String, Object> overview(Authentication auth) {
        Map<String, Object> result = new LinkedHashMap<>();
        FeeChangeRequest pending = feeSettingsService.pending();
        result.put("current", feeSettingsService.current());
        result.put("pending", pending);
        result.put("history", feeSettingsService.history());
        result.put("blockedReason", pending == null ? null : feeSettingsService.decisionBlockReason(pending, auth.getName()));
        return result;
    }

    public static class RejectRequest {
        public String note;
    }

    @PostMapping("/{id}/approve")
    public FeeChangeRequest approve(@PathVariable Long id, Authentication auth) {
        return feeSettingsService.approve(id, auth);
    }

    @PostMapping("/{id}/reject")
    public FeeChangeRequest reject(@PathVariable Long id, @RequestBody RejectRequest request, Authentication auth) {
        return feeSettingsService.reject(id, request.note, auth);
    }
}
