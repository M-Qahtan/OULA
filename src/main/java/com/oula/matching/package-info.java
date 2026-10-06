@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "intent",
                "property",
                "platform",
                "platform :: outbox"
        }
)
package com.oula.matching;
