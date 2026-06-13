package com.marketmind.model;

/**
 * Outcome of the deterministic guardrail post-check. A block is a HARD block:
 * the action is not executed and is logged to {@code guardrail_blocks}. There is
 * no silent clamping.
 */
public class GuardrailResult {

    private final boolean allowed;
    private final String reason;

    private GuardrailResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static GuardrailResult allowed() {
        return new GuardrailResult(true, null);
    }

    public static GuardrailResult blocked(String reason) {
        return new GuardrailResult(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
