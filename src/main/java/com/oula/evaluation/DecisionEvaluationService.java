package com.oula.evaluation;

import com.oula.advisory.HumanFeedbackSummary;
import com.oula.advisory.HumanReviewService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.interventions.InterventionHistory;
import com.oula.interventions.InterventionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded scientific evaluation of what has been documented, not whether
 * advice caused outcomes. No writes, training, thresholds or policy mutation.
 */
@Service
public class DecisionEvaluationService {
    public static final String PROTOCOL_VERSION = "decision-evidence-evaluation-v1";
    private final HumanReviewService rentalReviews;
    private final InterventionService interventions;
    private final Clock clock;

    @Autowired
    public DecisionEvaluationService(HumanReviewService rentalReviews,
                                     InterventionService interventions) {
        this(rentalReviews, interventions, Clock.systemUTC());
    }

    DecisionEvaluationService(HumanReviewService rentalReviews,
                              InterventionService interventions, Clock clock) {
        this.rentalReviews=rentalReviews;
        this.interventions=interventions;
        this.clock=clock;
    }

    @Transactional(readOnly=true)
    public DecisionEvaluationReport evaluate(AccessContext access, UUID propertyId) {
        Objects.requireNonNull(access,"access");
        Objects.requireNonNull(propertyId,"propertyId");
        if (access.purpose()!= AccessPurpose.PROPERTY_MANAGEMENT)
            throw new SecurityException("PROPERTY_MANAGEMENT purpose required");

        // Validates property in workspace before accessing any other domain.
        HumanFeedbackSummary rental=rentalReviews.summary(access,propertyId);
        if (!propertyId.equals(rental.propertyId()))
            throw new SecurityException("rental evidence property mismatch");

        DecisionEvaluationReport.RentalEvidence rentalEvidence =
                new DecisionEvaluationReport.RentalEvidence(
                        rental.capturedCases(),rental.casesWithHumanDecision(),
                        rental.casesWithOutcomeObservation(),
                        rental.casesWithImprovementObservation(),
                        rental.casesWithInconclusiveObservation(),
                        ratio(rental.casesWithOutcomeObservation(),rental.capturedCases())
                );

        String operationalSourceStatus = "AVAILABLE";
        DecisionEvaluationReport.OperationalEvidence operational;
        try {
            InterventionHistory history=interventions.history(access,propertyId);
            if (!propertyId.equals(history.propertyId())
                    || history.reviews().stream().anyMatch(r->
                            !propertyId.equals(r.propertyId())
                            || !access.workspaceId().equals(r.workspaceId()))
                    || history.outcomes().stream().anyMatch(o->
                            !propertyId.equals(o.propertyId())
                            || !access.workspaceId().equals(o.workspaceId()))) {
                throw new SecurityException("intervention evidence outside authorized property");
            }

            long observed=history.outcomes().size();
            long comparable=history.outcomes().stream()
                    .filter(o->!"NOT_COMPARABLE".equals(o.observedDirection())).count();
            long workOrders=history.outcomes().stream()
                    .filter(o->"VERIFIED_WORK_ORDER".equals(o.executionEvidenceLevel())).count();
            long improved=history.outcomes().stream()
                    .filter(o->"IMPROVED".equals(o.observedDirection())).count();
            long reviewed=history.reviews().size();
            operational=new DecisionEvaluationReport.OperationalEvidence(
                    reviewed,observed,comparable,workOrders,improved,ratio(observed,reviewed));
        } catch (NoSuchElementException missingVitalSnapshot) {
            // The property was already authorized via rental summary. Without a
            // managed Vital snapshot, operational observations are unavailable,
            // not proven absent. Do not replace unknown data with zero.
            operationalSourceStatus="NO_OPERATIONAL_VITAL_SNAPSHOT";
            operational=new DecisionEvaluationReport.OperationalEvidence(
                    null,null,null,null,null,null);
        }

        boolean anyObserved=rental.casesWithOutcomeObservation()>0
                || (operational.followUpObservations()!=null
                    && operational.followUpObservations()>0);
        String assessment=anyObserved ? "DESCRIPTIVE_EVIDENCE_ONLY"
                                     : "NO_DOCUMENTED_OUTCOME_OBSERVATIONS";
        return new DecisionEvaluationReport(
                access.workspaceId(),propertyId,PROTOCOL_VERSION,clock.instant(),
                assessment,operationalSourceStatus,rentalEvidence,operational,
                List.of(
                        "CAPTURED_RECOMMENDATIONS_ARE_NOT_ALL_ELIGIBLE_RECOMMENDATIONS",
                        "HUMAN_RECORDED_IMPROVEMENT_IS_NOT_VERIFIED_CAUSAL_EFFECT",
                        "DOCUMENTARY_RENT_COVERAGE_IS_NOT_BANK_CONFIRMED_COLLECTION",
                        "OPERATIONAL_STATUS_CHANGES_MAY_HAVE_EXTERNAL_CAUSES",
                        "HISTORICAL_COUNTS_ARE_ALL_TIME_WITHOUT_COHORT_NORMALIZATION",
                        "NO_LEGAL_OR_FINANCIAL_AUTOMATED_DECISION"),
                List.of(
                        "DEFINE_ELIGIBLE_RECOMMENDATION_DENOMINATOR_AND_COHORT",
                        "PRE_REGISTER_OUTCOME_METRIC_TIME_HORIZON_AND_EXCLUSIONS",
                        "MEASURE_BASELINE_AND_DATA_COMPLETENESS",
                        "ESTABLISH_APPROPRIATE_COMPARISON_GROUP_AND_CONFOUNDERS",
                        "INDEPENDENTLY_VERIFY_OUTCOMES_AND_DOCUMENT_PROVENANCE",
                        "REVIEW_PRIVACY_CONSENT_FAIRNESS_AND_HUMAN_APPROVAL",
                        "PERFORM_QUALIFIED_STATISTICAL_CAUSAL_REVIEW_BEFORE_ANY_EFFECT_CLAIM"),
                false);
    }

    private BigDecimal ratio(long numerator,long denominator) {
        if (denominator==0) return null; // undefined, not zero percent
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator),6,RoundingMode.HALF_UP);
    }
}
