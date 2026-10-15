@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "platform",
                "platform :: outbox",
                "platform :: audit"
        }
)
package com.oula.iam;
