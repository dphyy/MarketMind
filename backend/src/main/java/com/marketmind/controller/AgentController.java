package com.marketmind.controller;

import com.marketmind.dto.AgentCycleResult;
import com.marketmind.service.AgentCycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Triggers agent cycles. The demo's "Run Agent Cycle" button calls
 * {@code POST /api/agent/run-all}.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentCycleService agentCycleService;

    public AgentController(AgentCycleService agentCycleService) {
        this.agentCycleService = agentCycleService;
    }

    @PostMapping("/run/{productId}")
    public AgentCycleResult run(@PathVariable String productId) {
        return agentCycleService.runCycle(productId);
    }

    @PostMapping("/run-all")
    public List<AgentCycleResult> runAll() {
        return agentCycleService.runAll();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseStatusException notFound(IllegalArgumentException e) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
