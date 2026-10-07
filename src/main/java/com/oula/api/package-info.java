@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "transaction",
                "intelligence",
                "iam",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
