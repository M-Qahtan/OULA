@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.documents;
