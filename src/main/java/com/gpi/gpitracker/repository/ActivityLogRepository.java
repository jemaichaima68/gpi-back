package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, String> {

    List<ActivityLog> findAllByOrderByDateActionDesc();
    List<ActivityLog> findByActionOrderByDateActionDesc(String action);
    List<ActivityLog> findByPerformedByOrderByDateActionDesc(String performedBy);
    List<ActivityLog> findByEntityTypeOrderByDateActionDesc(String entityType);
}