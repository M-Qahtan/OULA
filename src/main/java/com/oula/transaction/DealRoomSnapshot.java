package com.oula.transaction;

import java.util.List;

public record DealRoomSnapshot(
        TransactionSnapshot transaction,
        List<ViewingRequestSnapshot> viewings,
        List<TransactionOfferSnapshot> offers
) {
    public DealRoomSnapshot {
        viewings = List.copyOf(viewings);
        offers = List.copyOf(offers);
    }
}
