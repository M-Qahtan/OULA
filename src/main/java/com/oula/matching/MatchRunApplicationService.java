package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.platform.UuidV7;
import com.oula.property.PropertyCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MatchRunApplicationService {
    private final CandidateRetrievalService retrieval = new CandidateRetrievalService();
    private final LifeFitCalculator lifeFit = new LifeFitCalculator();
    private final MatchRunRepository repository;
    private final Clock clock = Clock.systemUTC();

    public MatchRunApplicationService(MatchRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MatchRunOutcome run(Intent intent, List<PropertyCandidate> supply, UUID correlationId) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(correlationId, "correlationId");

        UUID runId = UuidV7.next();
        Instant startedAt = clock.instant();
        repository.start(runId, intent.workspaceId(), intent.id(), correlationId, startedAt);

        var scored = retrieval.retrieve(intent, supply).stream()
                .map(property -> {
                    LifeFitResult fit = lifeFit.score(intent, property);
                    return new UnrankedMatch(property.propertyId(), fit);
                })
                .sorted(Comparator
                        .comparingDouble((UnrankedMatch match) -> match.fit().score())
                        .reversed()
                        .thenComparing(UnrankedMatch::propertyId))
                .toList();

        AtomicInteger rank = new AtomicInteger(1);
        List<RankedMatch> ranked = scored.stream()
                .map(match -> new RankedMatch(
                        match.propertyId(),
                        rank.getAndIncrement(),
                        match.fit().score(),
                        match.fit().confidence(),
                        match.fit().dimensions()
                ))
                .toList();

        ranked.forEach(match -> repository.save(runId, match));
        repository.complete(runId, clock.instant());

        return new MatchRunOutcome(runId, ranked);
    }

    private record UnrankedMatch(UUID propertyId, LifeFitResult fit) {
    }
}
