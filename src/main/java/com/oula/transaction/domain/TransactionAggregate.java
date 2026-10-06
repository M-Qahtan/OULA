package com.oula.transaction.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit deal lifecycle. Illegal jumps are rejected in the domain layer. */
public final class TransactionAggregate {
    private static final Map<TransactionStage, Set<TransactionStage>> ALLOWED = allowedTransitions();

    private final UUID id;
    private final UUID workspaceId;
    private final UUID assetId;
    private TransactionStage stage;
    private TransactionStatus status;

    public TransactionAggregate(UUID id, UUID workspaceId, UUID assetId) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.assetId = assetId;
        this.stage = TransactionStage.DRAFT;
        this.status = TransactionStatus.OPEN;
    }

    public void transitionTo(TransactionStage target) {
        if (status != TransactionStatus.OPEN) throw new IllegalStateException("Closed transaction cannot transition");
        if (!ALLOWED.getOrDefault(stage, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal transaction transition: " + stage + " -> " + target);
        }
        stage = target;
        if (target == TransactionStage.COMPLETED) status = TransactionStatus.COMPLETED;
    }

    public void suspend() {
        if (status != TransactionStatus.OPEN) throw new IllegalStateException("Only open transactions can be suspended");
        status = TransactionStatus.SUSPENDED;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public UUID assetId() { return assetId; }
    public TransactionStage stage() { return stage; }
    public TransactionStatus status() { return status; }

    private static Map<TransactionStage, Set<TransactionStage>> allowedTransitions() {
        var map = new EnumMap<TransactionStage, Set<TransactionStage>>(TransactionStage.class);
        map.put(TransactionStage.DRAFT, EnumSet.of(TransactionStage.QUALIFIED));
        map.put(TransactionStage.QUALIFIED, EnumSet.of(TransactionStage.VIEWING));
        map.put(TransactionStage.VIEWING, EnumSet.of(TransactionStage.OFFERING));
        map.put(TransactionStage.OFFERING, EnumSet.of(TransactionStage.NEGOTIATING, TransactionStage.DUE_DILIGENCE));
        map.put(TransactionStage.NEGOTIATING, EnumSet.of(TransactionStage.DUE_DILIGENCE, TransactionStage.OFFERING));
        map.put(TransactionStage.DUE_DILIGENCE, EnumSet.of(TransactionStage.CONTRACTING));
        map.put(TransactionStage.CONTRACTING, EnumSet.of(TransactionStage.CLOSING));
        map.put(TransactionStage.CLOSING, EnumSet.of(TransactionStage.HANDOVER));
        map.put(TransactionStage.HANDOVER, EnumSet.of(TransactionStage.COMPLETED));
        map.put(TransactionStage.COMPLETED, Set.of());
        return Map.copyOf(map);
    }
}
