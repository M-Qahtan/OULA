package com.oula.api;

public record TransactionOfferCommandResponse(
        TransactionResponse transaction,
        TransactionOfferResponse offer
) {
}
