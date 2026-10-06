package com.oula.transaction;

import java.util.UUID;

public record TransactionSnapshot(
        UUID id,
        UUID workspaceId,
        TransactionStage stage,
        long version
) {
}
