package com.gpi.gpitracker.service;

import com.gpi.gpitracker.entity.ActivityLog;
import com.gpi.gpitracker.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository logRepository;

    public void log(String action, String entityType,
                    String entityId, String description,
                    String performedBy) {
        ActivityLog log = new ActivityLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setPerformedBy(performedBy);
        logRepository.save(log);
    }

    public List<ActivityLog> getAllLogs() {
        return logRepository.findAllByOrderByDateActionDesc();
    }

    public List<ActivityLog> getLogsByAction(String action) {
        return logRepository.findByActionOrderByDateActionDesc(action);
    }
}