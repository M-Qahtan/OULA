package com.oula.api;

import java.util.List;

public record DealRoomResponse(
        TransactionResponse transaction,
        List<TransactionViewingResponse> viewings,
        List<TransactionOfferResponse> offers,
        List<String> availableCommands,
        boolean humanConfirmationRequired
) {
    public DealRoomResponse {
        viewings = List.copyOf(viewings);
        offers = List.copyOf(offers);
        availableCommands = List.copyOf(availableCommands);
    }
}
