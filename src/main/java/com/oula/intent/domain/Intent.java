package com.oula.intent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Domain aggregate for a durable real-estate objective, not a search query. */
public final class Intent {
    private final UUID id;
    private final UUID workspaceId;
    private final UUID actorId;
    private final IntentType type;
    private final Currency currency;
    private final BigDecimal budgetMin;
    private final BigDecimal budgetMax;
    private final LocalDate targetFrom;
    private final LocalDate targetTo;
    private IntentStatus status;

    public Intent(UUID id, UUID workspaceId, UUID actorId, IntentType type, Currency currency,
                  BigDecimal budgetMin, BigDecimal budgetMax, LocalDate targetFrom, LocalDate targetTo) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.currency = Objects.requireNonNull(currency);
        if (budgetMin != null && budgetMax != null && budgetMin.compareTo(budgetMax) > 0) {
            throw new IllegalArgumentException("budgetMin must be <= budgetMax");
        }
        if (targetFrom != null && targetTo != null && targetFrom.isAfter(targetTo)) {
            throw new IllegalArgumentException("targetFrom must be <= targetTo");
        }
        this.budgetMin = budgetMin;
        this.budgetMax = budgetMax;
        this.targetFrom = targetFrom;
        this.targetTo = targetTo;
        this.status = IntentStatus.DRAFT;
    }

    public void activate() {
        if (status != IntentStatus.DRAFT && status != IntentStatus.PAUSED) {
            throw new IllegalStateException("Only DRAFT or PAUSED intents can be activated");
        }
        status = IntentStatus.ACTIVE;
    }

    public void pause() {
        if (status != IntentStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE intents can be paused");
        }
        status = IntentStatus.PAUSED;
    }

    public void fulfill() {
        if (status != IntentStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE intents can be fulfilled");
        }
        status = IntentStatus.FULFILLED;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public UUID actorId() { return actorId; }
    public IntentType type() { return type; }
    public IntentStatus status() { return status; }
    public Currency currency() { return currency; }
    public BigDecimal budgetMin() { return budgetMin; }
    public BigDecimal budgetMax() { return budgetMax; }
    public LocalDate targetFrom() { return targetFrom; }
    public LocalDate targetTo() { return targetTo; }
}
