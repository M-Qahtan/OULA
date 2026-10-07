@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "people",
                "intent",
                "property",
                "documents",
                "market",
                "matching",
                "intelligence",
                "orchestration",
                "transaction",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
