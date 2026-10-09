/** Read-only, human-supervised operational recommendations sourced from canonical Vital Signs. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"iam", "vitals", "tenancy", "documents", "platform", "platform :: audit", "platform :: outbox"})
package com.oula.advisory;
