package com.marketmind.repository;

import com.marketmind.model.SentimentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentimentEventRepository extends JpaRepository<SentimentEvent, Long> {

    Optional<SentimentEvent> findTopByCategoryOrderByRecordedAtDesc(String category);
}
