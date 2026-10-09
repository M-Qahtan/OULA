/**
 * Append-only human advisory review and descriptive follow-up outcomes.
 * Never executes a recommended action or converts correlation to causation.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "advisory", "vitals", "operations", "iam",
                "platform", "platform :: audit", "platform :: outbox"
        }
)
package com.oula.interventions;
