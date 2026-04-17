package com.gpi.gpitracker.dto;

import lombok.Data;

@Data
public class AgentDashboardStatsDto {
    private long totalTransactions;
    private long enAttente;
    private long acceptees;
    private long rejetees;
    private long signalees;
    private double montantTotal;
    private double montantMoyen;
}