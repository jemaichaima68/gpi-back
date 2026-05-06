package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.ClientConsultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClientConsultationRepository extends JpaRepository<ClientConsultation, Long> {

    List<ClientConsultation> findByClientEmailOrderByConsultedAtDesc(String clientEmail);
}