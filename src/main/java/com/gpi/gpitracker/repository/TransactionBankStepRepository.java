package com.gpi.gpitracker.repository;

import com.gpi.gpitracker.entity.TransactionBankStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface TransactionBankStepRepository extends JpaRepository<TransactionBankStep, Long> {

    List<TransactionBankStep> findByUetrAndClientEmailOrderByStepOrderAsc(String uetr, String clientEmail);

    List<TransactionBankStep> findByTransactionIdAndClientEmailOrderByStepOrderAsc(Long transactionId, String clientEmail);

    @Modifying
    @Transactional
    @Query("DELETE FROM TransactionBankStep t WHERE t.uetr = :uetr AND t.clientEmail = :clientEmail")
    void deleteByUetrAndClientEmail(@Param("uetr") String uetr, @Param("clientEmail") String clientEmail);
}