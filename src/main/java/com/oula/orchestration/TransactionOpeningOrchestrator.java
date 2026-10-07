package com.oula.orchestration;

import com.oula.iam.AccessContext;
import com.oula.intelligence.DecisionContextQuery;
import com.oula.intelligence.DecisionTransactionContext;
import com.oula.transaction.TransactionApplicationService;
import com.oula.transaction.TransactionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionOpeningOrchestrator {
    private final DecisionContextQuery decisions;
    private final TransactionApplicationService transactions;

    public TransactionOpeningOrchestrator(
            DecisionContextQuery decisions,
            TransactionApplicationService transactions
    ) {
        this.decisions = decisions;
        this.transactions = transactions;
    }

    @Transactional
    public TransactionSnapshot openFromDecision(
            AccessContext access,
            UUID decisionId,
            UUID correlationId
    ) {
        Objects.requireNonNull(access, "access");
        DecisionTransactionContext decision = decisions.forTransaction(access, decisionId);
        return transactions.open(
                decision.workspaceId(),
                decision.intentId(),
                decision.selectedPropertyId(),
                decision.decisionId(),
                access.actorId(),
                correlationId
        );
    }
}
