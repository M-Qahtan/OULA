@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "property",
                "transaction",
                "documents",
                "compliance",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.operations;
