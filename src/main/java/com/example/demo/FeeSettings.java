package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The Laucala Health Services Fee amounts in force. A single row (id 1). Only
// changed by an approved FeeChangeRequest (proposed by an admin, approved by a
// fee approver). The amount is copied onto each student at registration, so a
// change only affects students who register afterwards.
@Entity
@Table(name = "fee_settings")
public class FeeSettings {

    static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;
    private double localFee;    // students from Fiji
    private double regionalFee; // students from the other USP member countries
    private LocalDateTime updatedAt;
    private String updatedBy;  // the admin who proposed the change
    private String approvedBy; // the fee approver who approved it

    public FeeSettings() {}

    public FeeSettings(double localFee, double regionalFee) {
        this.localFee = localFee;
        this.regionalFee = regionalFee;
    }

    public Long getId() { return id; }

    public double getLocalFee() { return localFee; }
    public void setLocalFee(double localFee) { this.localFee = localFee; }

    public double getRegionalFee() { return regionalFee; }
    public void setRegionalFee(double regionalFee) { this.regionalFee = regionalFee; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
}
