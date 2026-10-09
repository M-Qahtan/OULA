@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam", "documents", "operations", "platform",
                "platform :: audit", "platform :: outbox"
        }
)
package com.oula.tenancy;
