package com.oula.agent;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P1/A1: a bounded, server-authorized planning kernel, NOT a generative model.
 * It NEVER invokes, signs, posts to, or even fetches a proposed domain tool.
 * Hypotheses are explicitly unverified and must not be promoted to facts.
 */
@Service
public final class CognitivePlanner {
    private static final String VERSION = "CPLANNER-1.0";
    private static final String ENGINE = "DETERMINISTIC_PLANNER_NOT_LLM";
    private static final String AUTHORITY = "HUMAN_DECIDES_SERVER_ENFORCES";
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("[\\u064B-\\u065F\\u0670]");
    private static final Pattern MATERIAL_ACTION = Pattern.compile(
            "(?:وقع\\s*(?:عقد|العقد|الاتفاق)|توقيع\\s*(?:عقد|العقد|اتفاق)|"
            + "ادفع|سدد|(?:حول|تحويل)\\s+(?:المال|مال|المبلغ|الاموال|اموال|الرصيد|رصيد)|"
            + "انقل\\s+الملكيه|نقل\\s+الملكيه|ابرم\\s+العقد|"
            + "\\b(?:sign|pay)\\b|transfer\\s+(?:funds|money|ownership)|submit\\s+offer|execute\\s+contract)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FORGE = Pattern.compile(
            "(?:هندس|معمار|انشا|تصميم مبن|مخطط بن|حساب احمال|bim|architect|structural|engineering|construction)");
    private static final Pattern CIVIC = Pattern.compile(
            "(?:مدين|تخطيط حضر|بنيه تحتيه|مخطط حضر|municipal|urban|city planning|zoning)");
    private static final Pattern PRISM = Pattern.compile(
            "(?:استثمار|عائد|جدوي|مخاطر|مقارن|قسط|تمويل|ربح|invest|yield|risk|compare|mortgage|finance|valuation)");
    private static final Pattern HABITAT = Pattern.compile(
            "(?:صيانه|تشغيل|ايجار|تاجير|مستاجر|اداره املاك|اداره عقار|contract renewal|maintenance|tenant|landlord|property management|rental)");
    private static final Pattern NEXUS = Pattern.compile(
            "(?:صفق|تفاوض|عقد|عرض شراء|معاين|وكاله|deal|offer|viewing|negotiat|transaction|contract)");
    private static final Pattern PULSE = Pattern.compile(
            "(?:منزل|بيت|شقه|فيلا|سكن|عقار|حي|شراء|عائل|home|house|apartment|housing|villa|property|buy|rent|family)");

    private record Tool(String code, String route, String scope, AccessPurpose purpose,
                        String mode) {}

    // The list names only pre-existing canonical Java routes. An allowlist is NOT
    // an authority grant, and "AVAILABLE_FOR_SEPARATE_REQUEST" is not execution.
    private static final List<Tool> CATALOG = List.of(
            new Tool("INTENT_READ", "/v1/intents/{intentId}", "oula.intent.read",
                    AccessPurpose.PROPERTY_DECISION_SUPPORT, "QUERY"),
            new Tool("PROPERTY_PASSPORT", "/v1/properties/{propertyId}/passport",
                    "oula.property.passport.read", AccessPurpose.PROPERTY_DECISION_SUPPORT, "QUERY"),
            new Tool("RECOMMENDATION_EXPLAIN",
                    "/v1/intelligence/recommendations/{recommendationId}/explain",
                    "oula.intelligence.read", AccessPurpose.PROPERTY_DECISION_SUPPORT, "QUERY"),
            new Tool("PROPERTY_CARE", "/v1/properties/{propertyId}/management",
                    "oula.property.management.read", AccessPurpose.PROPERTY_MANAGEMENT, "QUERY"),
            new Tool("DEAL_READ", "/v1/transactions/{transactionId}",
                    "oula.transaction.read", AccessPurpose.TRANSACTION_EXECUTION, "QUERY")
    );

    public CognitivePlan plan(AccessContext access, Set<String> grantedScopes, String rawGoal) {
        if (access == null || access.workspaceId() == null || access.actorId() == null
                || access.purpose() == null || grantedScopes == null)
            throw new IllegalArgumentException("scoped actor and purpose required");
        if (rawGoal == null || rawGoal.isBlank() || rawGoal.length() > 512)
            throw new IllegalArgumentException("goal must be between 1 and 512 characters");

        String goal = normalize(rawGoal);
        if (MATERIAL_ACTION.matcher(goal).find())
            return result(access, "NEXUS", "HIGH_IMPACT", "DENIED_ACTION", List.of(),
                    List.of(), List.of("Authenticated human approval and canonical domain authority required"));
        String world = detectWorld(goal);
        if (world == null)
            return result(access, "UNRESOLVED", "UNKNOWN", "NEEDS_CLARIFICATION",
                    List.of(), List.of(), List.of("Clarify the specific property, goal and intended activity"));
        if (world.equals("FORGE") || world.equals("CIVIC"))
            return result(access, world, "RESEARCH_HORIZON", "FUTURE_NOT_IMPLEMENTED",
                    List.of(), List.of(), List.of("No validated engineering/city simulation engine is available"));
        AccessPurpose required = switch (world) {
            case "HABITAT" -> AccessPurpose.PROPERTY_MANAGEMENT;
            case "NEXUS" -> AccessPurpose.TRANSACTION_EXECUTION;
            default -> AccessPurpose.PROPERTY_DECISION_SUPPORT;
        };
        if (access.purpose() != required)
            return result(access, world, "CONTEXT_SWITCH", "PURPOSE_MISMATCH",
                    List.of(), List.of(), List.of("Change to an independently authorized workspace purpose"));

        List<CognitivePlan.ToolProposal> proposed = new ArrayList<>();
        for (Tool tool : CATALOG) {
            if (tool.purpose() != required) continue;
            proposed.add(new CognitivePlan.ToolProposal(tool.code(), tool.route(), tool.scope(),
                    tool.mode(), grantedScopes.contains(tool.scope())
                            ? "AVAILABLE_FOR_SEPARATE_REQUEST" : "MISSING_SCOPE"));
        }
        List<CognitivePlan.ResearchHypothesis> hypotheses = hypotheses(world);
        List<String> missing = switch (world) {
            case "PULSE" -> List.of("Declared needs and hard constraints", "Property Passport source provenance",
                    "Travel and operating costs from permitted sources");
            case "PRISM" -> List.of("Comparable verified market observations", "Explicit cash-flow assumptions",
                    "Vacancy and financing downside scenarios");
            case "HABITAT" -> List.of("Authorized property identity", "Observed work-order and maintenance history",
                    "Applicable contract obligations");
            case "NEXUS" -> List.of("Counterparty authority", "Documentary checks and current transaction state",
                    "Explicit human decision on material actions");
            default -> List.of("Canonical domain evidence");
        };
        return result(access, world, world, "PLAN_ONLY", proposed, hypotheses, missing);
    }

    private static String normalize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        lower = DIACRITICS.matcher(lower).replaceAll("");
        lower = lower.replace('أ','ا').replace('إ','ا').replace('آ','ا')
                .replace('ى','ي').replace('ة','ه');
        return SPACE.matcher(lower.trim()).replaceAll(" ");
    }

    private static String detectWorld(String input) {
        if (FORGE.matcher(input).find()) return "FORGE";
        if (CIVIC.matcher(input).find()) return "CIVIC";
        if (NEXUS.matcher(input).find()) return "NEXUS";
        if (HABITAT.matcher(input).find()) return "HABITAT";
        if (PRISM.matcher(input).find()) return "PRISM";
        if (PULSE.matcher(input).find()) return "PULSE";
        return null;
    }

    private static List<CognitivePlan.ResearchHypothesis> hypotheses(String world) {
        // Counterfactual prompts, not computed market predictions or evidence.
        return switch (world) {
            case "PULSE" -> List.of(
                    hypothesis("Renting may provide flexibility", "Compare 3+ verified cost and mobility cases"),
                    hypothesis("Buying may improve long-term stability", "Compare financing, upkeep and future changes"),
                    hypothesis("Waiting may be more suitable", "Test budget resilience and downside assumptions"));
            case "PRISM" -> List.of(
                    hypothesis("Headline yield may mask vacancy risk", "Stress-test verified net cash flow"),
                    hypothesis("Location advantages may be priced in", "Use dated and comparable market evidence"));
            case "HABITAT" -> List.of(
                    hypothesis("Preventive maintenance may reduce repeat issues",
                            "Review authorized work-order history and contractor diagnosis"),
                    hypothesis("A reported issue may have multiple causes",
                            "Request an independent physical inspection"));
            case "NEXUS" -> List.of(
                    hypothesis("Missing evidence may block the transaction",
                            "Inspect canonical deal status and authority, no signing or submission"));
            default -> List.of();
        };
    }

    private static CognitivePlan.ResearchHypothesis hypothesis(String claim, String test) {
        return new CognitivePlan.ResearchHypothesis(claim, test, "UNVERIFIED_HYPOTHESIS");
    }

    private static CognitivePlan result(AccessContext access, String world, String category,
                                        String status, List<CognitivePlan.ToolProposal> tools,
                                        List<CognitivePlan.ResearchHypothesis> hypotheses,
                                        List<String> missing) {
        List<CognitivePlan.ReasoningStep> steps = List.of(
                new CognitivePlan.ReasoningStep("UNDERSTAND", "Classify goal and purpose", "DETERMINISTIC"),
                new CognitivePlan.ReasoningStep("PLAN", "List scope-filtered canonical tools; never call them", "PROPOSED_ONLY"),
                new CognitivePlan.ReasoningStep("IMAGINE", "Produce explicitly unverified counterfactual hypotheses", "HYPOTHESIS_ONLY"),
                new CognitivePlan.ReasoningStep("CRITIQUE", "Demand source provenance, limitations and adversarial scenarios", "PENDING_EVIDENCE"),
                new CognitivePlan.ReasoningStep("DECIDE", "Human decides; Java policy and authorization control execution", "HUMAN_REQUIRED"),
                new CognitivePlan.ReasoningStep("LEARN", "Compare later observed outcomes in existing RealityGap, never self-train here", "NOT_EXECUTED")
        );
        return new CognitivePlan(VERSION, access.workspaceId(), access.purpose().name(),
                world, category, status, ENGINE, AUTHORITY, steps, List.copyOf(tools),
                List.copyOf(hypotheses), List.copyOf(missing),
                List.of("No LLM calls or domain-tool execution",
                        "Client text cannot grant scopes, consent, approval or factual provenance",
                        "Inference is not VERIFIED; future research is not a deployed engine",
                        "No persistent personal memory, autonomous learning or background work"));
    }
}
