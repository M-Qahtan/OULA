package com.oula.agent;

import java.util.List;
import java.util.UUID;

/**
 * A non-executing epistemic plan. Nothing here is a verified real-estate fact,
 * human decision, approval, model output or authorization to run a tool.
 */
public record CognitivePlan(
        String version,
        UUID workspaceId,
        String purpose,
        String world,
        String category,
        String status,
        String engine,
        String authority,
        List<ReasoningStep> steps,
        List<ToolProposal> tools,
        List<ResearchHypothesis> hypotheses,
        List<String> missingEvidence,
        List<String> guardrails
) {
    public record ReasoningStep(String phase, String instruction, String status) {}
    public record ToolProposal(String code, String route, String requiredScope,
                               String mode, String availability) {}
    public record ResearchHypothesis(String statement, String validationMethod,
                                     String evidenceStatus) {}
}
