@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "matching",
                "iam",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.intelligence;
