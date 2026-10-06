@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "documents",
                "iam",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.intelligence;
