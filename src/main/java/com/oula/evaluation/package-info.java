/**
 * Read-only evidence coverage evaluation. Not a model trainer or policy-authority module.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"iam", "advisory", "interventions"}
)
package com.oula.evaluation;
