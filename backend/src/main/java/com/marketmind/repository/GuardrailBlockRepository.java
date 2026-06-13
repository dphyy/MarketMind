package com.marketmind.repository;

import com.marketmind.model.GuardrailBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuardrailBlockRepository extends JpaRepository<GuardrailBlock, Long> {

    List<GuardrailBlock> findAllByOrderByCreatedAtDesc();
}
