@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "compliance",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.leasing;
