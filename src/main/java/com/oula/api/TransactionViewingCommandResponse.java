package com.oula.api;

public record TransactionViewingCommandResponse(
        TransactionResponse transaction,
        TransactionViewingResponse viewing
) {
}
