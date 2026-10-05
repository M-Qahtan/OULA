package com.oula.transaction;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
public final class TransactionStateMachine {
  private static final Map<TransactionStage, Set<TransactionStage>> ALLOWED = allowedTransitions();
  public TransactionStage transition(TransactionStage current, TransactionStage target) {
    if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) throw new IllegalStateException("illegal transaction transition: " + current + " -> " + target);
    return target;
  }
  private static Map<TransactionStage, Set<TransactionStage>> allowedTransitions() {
    Map<TransactionStage, Set<TransactionStage>> map = new EnumMap<>(TransactionStage.class);
    map.put(TransactionStage.DRAFT, EnumSet.of(TransactionStage.QUALIFIED, TransactionStage.CANCELLED));
    map.put(TransactionStage.QUALIFIED, EnumSet.of(TransactionStage.VIEWING, TransactionStage.CANCELLED));
    map.put(TransactionStage.VIEWING, EnumSet.of(TransactionStage.OFFERING, TransactionStage.CANCELLED));
    map.put(TransactionStage.OFFERING, EnumSet.of(TransactionStage.NEGOTIATING, TransactionStage.DUE_DILIGENCE, TransactionStage.CANCELLED));
    map.put(TransactionStage.NEGOTIATING, EnumSet.of(TransactionStage.DUE_DILIGENCE, TransactionStage.CANCELLED));
    map.put(TransactionStage.DUE_DILIGENCE, EnumSet.of(TransactionStage.CONTRACTING, TransactionStage.CANCELLED));
    map.put(TransactionStage.CONTRACTING, EnumSet.of(TransactionStage.CLOSING, TransactionStage.CANCELLED));
    map.put(TransactionStage.CLOSING, EnumSet.of(TransactionStage.HANDOVER, TransactionStage.CANCELLED));
    map.put(TransactionStage.HANDOVER, EnumSet.of(TransactionStage.COMPLETED));
    map.put(TransactionStage.COMPLETED, EnumSet.noneOf(TransactionStage.class));
    map.put(TransactionStage.CANCELLED, EnumSet.noneOf(TransactionStage.class));
    return Map.copyOf(map);
  }
}
