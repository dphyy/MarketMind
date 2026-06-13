package com.marketmind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "guardrail_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuardrailBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private ActionType actionType;

    @Column(name = "proposed_value")
    private String proposedValue;

    @Column(name = "block_reason")
    private String blockReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
