package com.marketmind.repository;

import com.marketmind.model.ActionLog;
import com.marketmind.model.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

    List<ActionLog> findAllByOrderByCreatedAtDesc();

    List<ActionLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    // Used by the guardrail engine to compute cumulative daily price movement.
    List<ActionLog> findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
            String productId, ActionType actionType, LocalDateTime since);
}
