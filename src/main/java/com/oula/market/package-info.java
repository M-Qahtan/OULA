@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "iam",
                "property",
                "spatial",
                "platform",
                "platform :: audit",
                "platform :: outbox"
        }
)
package com.oula.market;
