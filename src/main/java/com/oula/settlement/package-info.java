@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "operations",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.settlement;
