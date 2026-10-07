package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeeSettingsService {

    // Used until an admin first saves the fees (the amounts that were hard-coded before).
    private static final double DEFAULT_LOCAL_FEE = 50.00;
    private static final double DEFAULT_REGIONAL_FEE = 80.00;
    private static final double MAX_FEE = 10_000.00;

    private final FeeSettingsRepository feeSettingsRepository;
    private final AuditLogService auditLogService;

    public FeeSettingsService(FeeSettingsRepository feeSettingsRepository, AuditLogService auditLogService) {
        this.feeSettingsRepository = feeSettingsRepository;
        this.auditLogService = auditLogService;
    }

    public FeeSettings current() {
        return feeSettingsRepository.findById(FeeSettings.SINGLETON_ID)
                .orElseGet(() -> new FeeSettings(DEFAULT_LOCAL_FEE, DEFAULT_REGIONAL_FEE));
    }

    public FeeSettings update(Double localFee, Double regionalFee, Authentication auth) {
        validate(localFee, "Local fee");
        validate(regionalFee, "Regional fee");

        FeeSettings settings = current();
        String change = describe("Local fee", settings.getLocalFee(), localFee)
                + describe("Regional fee", settings.getRegionalFee(), regionalFee);
        if (change.isEmpty()) return settings;

        settings.setLocalFee(localFee);
        settings.setRegionalFee(regionalFee);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(auth.getName());
        FeeSettings saved = feeSettingsRepository.save(settings);
        auditLogService.recordAccess(auth, "UPDATE_FEES", null, change.trim() + " Applies to new registrations.");
        return saved;
    }

    private static void validate(Double fee, String label) {
        if (fee == null || fee.isNaN() || fee <= 0 || fee > MAX_FEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " must be more than FJ$0 and at most FJ$" + String.format(Locale.ROOT, "%,.2f", MAX_FEE) + ".");
        }
        if (BigDecimal.valueOf(fee).stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " can have at most 2 decimal places.");
        }
    }

    private static String describe(String label, double from, double to) {
        if (Double.compare(from, to) == 0) return "";
        return String.format(Locale.ROOT, "%s changed FJ$%.2f -> FJ$%.2f. ", label, from, to);
    }
}
