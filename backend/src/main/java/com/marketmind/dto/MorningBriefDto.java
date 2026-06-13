package com.marketmind.dto;

import java.time.LocalDate;

/**
 * The morning brief returned to the dashboard. {@code source} is "LIVE" (Kimi
 * prose) or "MOCK" (rule-based narrative).
 */
public record MorningBriefDto(
        LocalDate date,
        int totalActions,
        int executedCount,
        int blockedCount,
        String narrative,
        String source
) {
}
