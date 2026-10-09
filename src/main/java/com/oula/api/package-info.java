@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching", "transaction", "intelligence", "property",
                "operations", "services", "settlement", "compliance",
                "integration", "vitals", "advisory", "interventions", "evaluation", "evaluation",
                "tenancy", "iam", "platform", "platform :: idempotency",
                "platform :: outbox"
        }
)
package com.oula.api;
