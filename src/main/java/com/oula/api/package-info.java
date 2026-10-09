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
                "integration",
                "vitals",
                "leasing",
                "iam",
                "platform",
                "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
