package com.oula.matching;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MatchingFacade {
    private final MatchingQueryRepository queryRepository;
    private final MatchRunApplicationService matchRunService;

    public MatchingFacade(
            MatchingQueryRepository queryRepository,
            MatchRunApplicationService matchRunService
    ) {
        this.queryRepository = queryRepository;
        this.matchRunService = matchRunService;
    }

    public MatchRunOutcome run(UUID intentId, UUID workspaceId, UUID correlationId) {
        var intent = queryRepository.loadIntent(intentId, workspaceId);
        var candidates = queryRepository.loadCandidates(intentId, workspaceId);
        return matchRunService.run(intent, candidates, correlationId);
    }
}
