/**
 * Read-only evidence coverage and calibration-readiness evaluation.
 * Not a model trainer, causal-effect engine or policy-authority module.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"iam", "advisory", "interventions", "intelligence"}
)
package com.oula.evaluation;
