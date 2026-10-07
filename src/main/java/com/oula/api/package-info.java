@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "transaction",
                "intelligence",
                "property",
                "iam",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
