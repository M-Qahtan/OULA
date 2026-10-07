@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "property",
                "transaction",
                "documents",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.operations;
