@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "transaction",
                "intelligence",
                "property",
                "operations",
                "services",
                "settlement",
                "iam",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
