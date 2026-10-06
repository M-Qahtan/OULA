package com.oula.platform.outbox;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringApplicationEventSink implements EventSink {
    private final ApplicationEventPublisher publisher;

    SpringApplicationEventSink(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(StoredOutboxEvent event) {
        publisher.publishEvent(event);
    }
}
