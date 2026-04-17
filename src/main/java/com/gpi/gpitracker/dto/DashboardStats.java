package com.gpi.gpitracker.dto;

import com.gpi.gpitracker.entity.AppUser;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.Map;
@Data
public class DashboardStats {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long addedThisMonth;
    private Map<String, Long> byRole;
    private List<AppUser> recentUsers;
    private Map<String, Long> registrationsByMonth;

    // Ajoutez ce champ si nécessaire
    private Map<String, Long> activityByDay;
}