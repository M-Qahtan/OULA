@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "transaction",
                "intelligence",
                "property",
                "operations",
                "services",
                "settlement",
                "compliance",
                "iam",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
