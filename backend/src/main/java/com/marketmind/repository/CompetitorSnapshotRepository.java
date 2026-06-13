package com.marketmind.repository;

import com.marketmind.model.CompetitorSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitorSnapshotRepository extends JpaRepository<CompetitorSnapshot, Long> {

    List<CompetitorSnapshot> findByProductIdOrderByScrapedAtDesc(String productId);
}
