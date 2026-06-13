package com.marketmind.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The proposed action for one product in one cycle. Produced by
 * {@code KimiReasoningService} (live Kimi JSON, or the rule-based fallback) and
 * then validated/mutated by the deterministic {@code GuardrailEngine}.
 *
 * <p>Field names map to the JSON schema in the Kimi system prompt via
 * {@link JsonProperty} so the live model response deserialises directly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyDecision {

    @JsonProperty("recommended_mode")
    private StrategyMode recommendedMode;

    @JsonProperty("mode_reason")
    private String modeReason;

    @JsonProperty("action_type")
    private ActionType actionType;

    @JsonProperty("proposed_price")
    private BigDecimal proposedPrice;

    @JsonProperty("proposed_ad_bid")
    private BigDecimal proposedAdBid;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("plain_english_explanation")
    private String plainEnglishExplanation;

    /** "LIVE" (Kimi/TokenRouter) or "MOCK" (rule-based fallback). Not sent to the model. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String source;
}
