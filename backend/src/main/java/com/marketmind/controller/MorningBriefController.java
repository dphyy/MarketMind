package com.marketmind.controller;

import com.marketmind.dto.MorningBriefDto;
import com.marketmind.service.MorningBriefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the seller's morning brief.
 */
@RestController
@RequestMapping("/api/brief")
public class MorningBriefController {

    private final MorningBriefService morningBriefService;

    public MorningBriefController(MorningBriefService morningBriefService) {
        this.morningBriefService = morningBriefService;
    }

    @GetMapping("/morning")
    public MorningBriefDto morning() {
        return morningBriefService.getMorningBrief();
    }

    @PostMapping("/generate")
    public MorningBriefDto generate() {
        return morningBriefService.regenerate();
    }
}
