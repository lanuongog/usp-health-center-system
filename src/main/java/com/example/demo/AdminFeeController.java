package com.example.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Admin side of the Health Services Fee (secured by /admin/**). Admins can no
// longer change the fee directly - they propose a change, which a fee approver
// must approve (see FeeSettingsService).
@RestController
@RequestMapping("/admin/fees")
public class AdminFeeController {

    private final FeeSettingsService feeSettingsService;
    private final NotificationRepository notificationRepository;

    public AdminFeeController(FeeSettingsService feeSettingsService, NotificationRepository notificationRepository) {
        this.feeSettingsService = feeSettingsService;
        this.notificationRepository = notificationRepository;
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

    // Finance's approve/reject answers to fee changes THIS admin proposed (newest first).
    @GetMapping("/notifications")
    public List<Notification> myFeeNotifications(Authentication auth) {
        return notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(auth.getName()).stream()
                .filter(n -> n.getType() != null && n.getType().startsWith("FEE_CHANGE_"))
                .limit(20)
                .toList();
    }

    @PostMapping("/notifications/{id}/read")
    public Notification markFeeNotificationRead(@PathVariable Long id, Authentication auth) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> auth.getName().equals(x.getRecipientUsername()))
                .filter(x -> x.getType() != null && x.getType().startsWith("FEE_CHANGE_"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
        return notificationRepository.save(n);
    }

    @PostMapping("/proposals/{id}/withdraw")
    public FeeChangeRequest withdraw(@PathVariable Long id, Authentication auth) {
        return feeSettingsService.withdraw(id, auth);
    }
}
