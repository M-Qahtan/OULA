package com.oula.intelligence;

import com.oula.iam.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class DecisionContextQuery {
    private final IntelligenceRepository repository;

    public DecisionContextQuery(IntelligenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DecisionTransactionContext forTransaction(
            AccessContext access,
            UUID decisionId
    ) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(decisionId, "decisionId");
        DecisionRow row = repository.decision(decisionId, access.workspaceId());
        return new DecisionTransactionContext(
                row.id(),
                row.workspaceId(),
                row.recommendationId(),
                row.intentId(),
                row.selectedPropertyId(),
                row.acceptedRecommendation(),
                row.modelVersionId()
        );
    }
}
