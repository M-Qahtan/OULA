package com.oula.platform.outbox;

public interface EventSink {
    void publish(StoredOutboxEvent event);
}
