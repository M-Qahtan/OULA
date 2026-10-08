@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "operations",
                "compliance",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.settlement;
