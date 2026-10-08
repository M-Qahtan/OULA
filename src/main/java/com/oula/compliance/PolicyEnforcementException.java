package com.oula.compliance;

public class PolicyEnforcementException extends RuntimeException {
    private final PolicyDecision policyDecision;

    public PolicyEnforcementException(PolicyDecision policyDecision) {
        super("policy decision " + policyDecision.decision()
                + ": " + String.join(",", policyDecision.reasonCodes()));
        this.policyDecision = policyDecision;
    }

    public PolicyDecision policyDecision() {
        return policyDecision;
    }
}
