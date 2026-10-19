package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A proposed change to the Health Services Fee. An admin proposes it; it only
// takes effect once a different person with the FEE_APPROVER role approves it
// (two-person rule - one person can't change what students are charged on
// their own). Kept after the decision as the fee's change history.
@Entity
@Table(name = "fee_change_requests")
public class FeeChangeRequest {

    static final String PENDING = "PENDING";
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";
    static final String WITHDRAWN = "WITHDRAWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double previousLocalFee;
    private double previousRegionalFee;
    private double proposedLocalFee;
    private double proposedRegionalFee;
    @Column(columnDefinition = "TEXT")
    private String reason;
    private String status;
    private String proposedBy;
    private LocalDateTime proposedAt;
    private String decidedBy;
    private LocalDateTime decidedAt;
    @Column(columnDefinition = "TEXT")
    private String decisionNote; // the approver's reason for rejecting

    public FeeChangeRequest() {}

    public Long getId() { return id; }

    public double getPreviousLocalFee() { return previousLocalFee; }
    public void setPreviousLocalFee(double previousLocalFee) { this.previousLocalFee = previousLocalFee; }

    public double getPreviousRegionalFee() { return previousRegionalFee; }
    public void setPreviousRegionalFee(double previousRegionalFee) { this.previousRegionalFee = previousRegionalFee; }

    public double getProposedLocalFee() { return proposedLocalFee; }
    public void setProposedLocalFee(double proposedLocalFee) { this.proposedLocalFee = proposedLocalFee; }

    public double getProposedRegionalFee() { return proposedRegionalFee; }
    public void setProposedRegionalFee(double proposedRegionalFee) { this.proposedRegionalFee = proposedRegionalFee; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }

    public LocalDateTime getProposedAt() { return proposedAt; }
    public void setProposedAt(LocalDateTime proposedAt) { this.proposedAt = proposedAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
