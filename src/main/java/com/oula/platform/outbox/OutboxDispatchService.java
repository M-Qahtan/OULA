package com.oula.platform.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatchService {
    private final OutboxDispatchRepository repository;
    private final EventSink sink;

    public OutboxDispatchService(OutboxDispatchRepository repository, EventSink sink) {
        this.repository = repository;
        this.sink = sink;
    }

    @Transactional
    public int dispatchBatch(int limit) {
        if (limit <= 0 || limit > 500) {
            throw new IllegalArgumentException("outbox batch size must be between 1 and 500");
        }

        int published = 0;
        for (StoredOutboxEvent event : repository.lockNextBatch(limit)) {
            try {
                sink.publish(event);
                repository.markPublished(event.eventId());
                published++;
            } catch (RuntimeException ex) {
                repository.markFailed(event.eventId(), ex.getMessage());
            }
        }
        return published;
    }
}
