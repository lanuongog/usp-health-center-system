package com.example.demo;

import java.util.Map;

public class DashboardSummary {
    private long totalAppointments;
    private long noShowCount;
    private double noShowRate;
    private Map<String, Long> byVisitReason;
    private Map<String, Long> byDoctor;

    public DashboardSummary(long totalAppointments, long noShowCount, double noShowRate,
                             Map<String, Long> byVisitReason, Map<String, Long> byDoctor) {
        this.totalAppointments = totalAppointments;
        this.noShowCount = noShowCount;
        this.noShowRate = noShowRate;
        this.byVisitReason = byVisitReason;
        this.byDoctor = byDoctor;
    }

    public long getTotalAppointments() { return totalAppointments; }
    public long getNoShowCount() { return noShowCount; }
    public double getNoShowRate() { return noShowRate; }
    public Map<String, Long> getByVisitReason() { return byVisitReason; }
    public Map<String, Long> getByDoctor() { return byDoctor; }
}
