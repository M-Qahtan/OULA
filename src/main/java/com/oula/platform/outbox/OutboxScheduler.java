package com.oula.platform.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oula.outbox.dispatch-enabled", havingValue = "true")
class OutboxScheduler {
    private final OutboxDispatchService dispatcher;

    OutboxScheduler(OutboxDispatchService dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${oula.outbox.dispatch-delay-ms:1000}")
    void dispatch() {
        dispatcher.dispatchBatch(100);
    }
}
