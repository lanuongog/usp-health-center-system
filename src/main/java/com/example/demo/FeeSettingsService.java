package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Health Services Fee, under a two-person rule: an admin proposes a change with
// a reason, and it only takes effect when someone with the FEE_APPROVER role -
// never the person who proposed it - approves it. Only one proposal can wait at
// a time. Every step is audit-logged, and the proposals are the fee's history.
@Service
public class FeeSettingsService {

    // Used until the first approved change (the amounts that were hard-coded before).
    private static final double DEFAULT_LOCAL_FEE = 50.00;
    private static final double DEFAULT_REGIONAL_FEE = 80.00;
    private static final double MAX_FEE = 10_000.00;
    private static final int MAX_NOTE_LENGTH = 500;
    static final String APPROVER_ROLE = "FEE_APPROVER";

    private final FeeSettingsRepository feeSettingsRepository;
    private final FeeChangeRequestRepository changeRequestRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationRepository notificationRepository;

    public FeeSettingsService(FeeSettingsRepository feeSettingsRepository,
                              FeeChangeRequestRepository changeRequestRepository,
                              UserRepository userRepository,
                              AuditLogService auditLogService,
                              NotificationRepository notificationRepository) {
        this.feeSettingsRepository = feeSettingsRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationRepository = notificationRepository;
    }

    public FeeSettings current() {
        return feeSettingsRepository.findById(FeeSettings.SINGLETON_ID)
                .orElseGet(() -> new FeeSettings(DEFAULT_LOCAL_FEE, DEFAULT_REGIONAL_FEE));
    }

    public FeeChangeRequest pending() {
        return changeRequestRepository.findFirstByStatus(FeeChangeRequest.PENDING).orElse(null);
    }

    public List<FeeChangeRequest> history() {
        return changeRequestRepository.findTop50ByOrderByProposedAtDesc();
    }

    public boolean approverExists() {
        return !userRepository.findByRoleAndActiveTrue(APPROVER_ROLE).isEmpty();
    }

    // Admin: propose new amounts. Nothing changes for students until approved.
    @Transactional
    public synchronized FeeChangeRequest propose(Double localFee, Double regionalFee, String reason, Authentication auth) {
        validate(localFee, "Local fee");
        validate(regionalFee, "Regional fee");
        String why = requireNote(reason, "Give a reason for the change so the approver knows why.");
        if (pending() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A fee change is already waiting for approval. Withdraw it first to propose a different one.");
        }
        if (!approverExists()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No fee approver account is set up yet, so a change couldn't be approved. Ask the developers to create one.");
        }
        FeeSettings settings = current();
        String change = describe("Local fee", settings.getLocalFee(), localFee)
                + describe("Regional fee", settings.getRegionalFee(), regionalFee);
        if (change.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "These are already the current fees.");
        }

        FeeChangeRequest request = new FeeChangeRequest();
        request.setPreviousLocalFee(settings.getLocalFee());
        request.setPreviousRegionalFee(settings.getRegionalFee());
        request.setProposedLocalFee(localFee);
        request.setProposedRegionalFee(regionalFee);
        request.setReason(why);
        request.setStatus(FeeChangeRequest.PENDING);
        request.setProposedBy(auth.getName());
        request.setProposedAt(LocalDateTime.now());
        FeeChangeRequest saved = changeRequestRepository.save(request);
        auditLogService.recordAccess(auth, "FEE_CHANGE_PROPOSED", null,
                "Proposal #" + saved.getId() + ": " + change.trim() + " Reason: " + why);
        return saved;
    }

    // Admin: take back a proposal still waiting (e.g. a typo).
    @Transactional
    public synchronized FeeChangeRequest withdraw(Long id, Authentication auth) {
        FeeChangeRequest request = findPending(id);
        decide(request, FeeChangeRequest.WITHDRAWN, null, auth);
        auditLogService.recordAccess(auth, "FEE_CHANGE_WITHDRAWN", null, "Proposal #" + id + " withdrawn");
        return request;
    }

    // Fee approver: the change takes effect now, for students who register from here on.
    @Transactional
    public synchronized FeeChangeRequest approve(Long id, Authentication auth) {
        FeeChangeRequest request = findPending(id);
        requireDifferentPerson(request, auth);
        FeeSettings settings = current();
        String change = describe("Local fee", settings.getLocalFee(), request.getProposedLocalFee())
                + describe("Regional fee", settings.getRegionalFee(), request.getProposedRegionalFee());
        settings.setLocalFee(request.getProposedLocalFee());
        settings.setRegionalFee(request.getProposedRegionalFee());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(request.getProposedBy());
        settings.setApprovedBy(auth.getName());
        feeSettingsRepository.save(settings);
        decide(request, FeeChangeRequest.APPROVED, null, auth);
        auditLogService.recordAccess(auth, "FEE_CHANGE_APPROVED", null,
                "Proposal #" + id + " by " + request.getProposedBy() + " approved: " + change.trim()
                        + " Applies to new registrations.");
        notifyProposer(request, "FEE_CHANGE_APPROVED",
                "Finance (" + auth.getName() + ") approved your fee change: " + change.trim()
                        + " Applies to new registrations.");
        return request;
    }

    // Fee approver: turn it down, with a reason the admin will see.
    @Transactional
    public synchronized FeeChangeRequest reject(Long id, String note, Authentication auth) {
        String why = requireNote(note, "Give a reason for rejecting so the admin knows what to change.");
        FeeChangeRequest request = findPending(id);
        requireDifferentPerson(request, auth);
        decide(request, FeeChangeRequest.REJECTED, why, auth);
        auditLogService.recordAccess(auth, "FEE_CHANGE_REJECTED", null,
                "Proposal #" + id + " by " + request.getProposedBy() + " rejected. Reason: " + why);
        notifyProposer(request, "FEE_CHANGE_REJECTED",
                "Finance (" + auth.getName() + ") rejected your fee change. Reason: " + why);
        return request;
    }

    // Tells the admin who proposed the change that Finance has responded. Shown in
    // that admin's bell (recipientUsername = the admin, so it never appears in the
    // nurse/doctor staff feed or a student's feed).
    private void notifyProposer(FeeChangeRequest request, String type, String message) {
        Notification n = new Notification();
        n.setType(type);
        n.setRecipientUsername(request.getProposedBy());
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    private FeeChangeRequest findPending(Long id) {
        FeeChangeRequest request = changeRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee change not found."));
        if (!FeeChangeRequest.PENDING.equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This fee change has already been decided.");
        }
        return request;
    }

    // Any Finance account may decide, except on a change it proposed itself.
    // Returns why this approver can't decide, or null if they can.
    public String decisionBlockReason(FeeChangeRequest request, String approverUsername) {
        if (approverUsername.equals(request.getProposedBy())) {
            return "You proposed this change, so another approver must decide on it.";
        }
        return null;
    }

    private void requireDifferentPerson(FeeChangeRequest request, Authentication auth) {
        String reason = decisionBlockReason(request, auth.getName());
        if (reason != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }
    }

    private void decide(FeeChangeRequest request, String status, String note, Authentication auth) {
        request.setStatus(status);
        request.setDecidedBy(auth.getName());
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note);
        changeRequestRepository.save(request);
    }

    private static String requireNote(String note, String message) {
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (note.trim().length() > MAX_NOTE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the reason to " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return note.trim();
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
        return String.format(Locale.ROOT, "%s FJ$%.2f -> FJ$%.2f. ", label, from, to);
    }
}
