package com.oula.evaluation;

import com.oula.advisory.HumanFeedbackSummary;
import com.oula.advisory.HumanReviewService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.interventions.InterventionHistory;
import com.oula.interventions.InterventionOutcome;
import com.oula.interventions.InterventionReview;
import com.oula.interventions.InterventionService;
import com.oula.vitals.VitalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionEvaluationServiceTest {
    private final UUID workspace=UUID.randomUUID();
    private final UUID property=UUID.randomUUID();
    private final UUID actor=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-10-10T06:00:00Z");
    private final AccessContext access=new AccessContext(
            actor,"evaluation-test",workspace,AccessPurpose.PROPERTY_MANAGEMENT);

    private HumanReviewService rental;
    private InterventionService interventions;
    private DecisionEvaluationService service;

    @BeforeEach
    void setup() {
        rental=mock(HumanReviewService.class);
        interventions=mock(InterventionService.class);
        service=new DecisionEvaluationService(rental,interventions,Clock.fixed(now,ZoneOffset.UTC));
    }

    @Test
    void descriptiveCoverageIsNotClaimedAsCausalEffectOrValidatedAccuracy() {
        when(rental.summary(access,property)).thenReturn(new HumanFeedbackSummary(
                property,5,3,2,1,1,"DOCUMENTARY_ONLY"));
        UUID review1=UUID.randomUUID(),review2=UUID.randomUUID();
        when(interventions.history(access,property)).thenReturn(
                new InterventionHistory(property,List.of(review(review1),review(review2)),
                        List.of(outcome(review1,"IMPROVED","OBSERVATION_ONLY"),
                                outcome(review2,"NOT_COMPARABLE","VERIFIED_WORK_ORDER"))));

        var result=service.evaluate(access,property);

        assertThat(result.rental().capturedReviews()).isEqualTo(5);
        assertThat(result.rental().observationCoverageOfCaptured())
                .isEqualByComparingTo("0.400000");
        assertThat(result.operational().recordedReviews()).isEqualTo(2);
        assertThat(result.operational().comparableStatusObservations()).isEqualTo(1);
        assertThat(result.operational().evidenceBackedWorkOrders()).isEqualTo(1);
        assertThat(result.operational().followUpCoverageOfReviewed())
                .isEqualByComparingTo("1.000000");
        assertThat(result.evidenceAssessment()).isEqualTo("DESCRIPTIVE_EVIDENCE_ONLY");
        assertThat(result.automaticTrainingAllowed()).isFalse();
        assertThat(result.researchPrerequisites()).contains(
                "ESTABLISH_APPROPRIATE_COMPARISON_GROUP_AND_CONFOUNDERS");
        assertThat(result.limitations()).contains(
                "HUMAN_RECORDED_IMPROVEMENT_IS_NOT_VERIFIED_CAUSAL_EFFECT");
        verifyNoMoreInteractions(rental,interventions);
    }

    @Test
    void missingVitalSnapshotIsUnavailableNotZeroOperationalEvidence() {
        when(rental.summary(access,property)).thenReturn(new HumanFeedbackSummary(
                property,0,0,0,0,0,"DOCUMENTARY_ONLY"));
        when(interventions.history(access,property)).thenThrow(
                new NoSuchElementException("no vital snapshot"));
        var result=service.evaluate(access,property);
        assertThat(result.operationalSourceStatus()).isEqualTo("NO_OPERATIONAL_VITAL_SNAPSHOT");
        assertThat(result.operational().recordedReviews()).isNull();
        assertThat(result.operational().followUpCoverageOfReviewed()).isNull();
        assertThat(result.rental().observationCoverageOfCaptured()).isNull();
        assertThat(result.evidenceAssessment())
                .isEqualTo("NO_DOCUMENTED_OUTCOME_OBSERVATIONS");
    }

    @Test
    void rejectsCrossWorkspaceAndIncorrectPurposeWithoutSourceDisclosure() {
        AccessContext wrongPurpose=new AccessContext(actor,"test",workspace,
                AccessPurpose.PROPERTY_DECISION_SUPPORT);
        assertThatThrownBy(()->service.evaluate(wrongPurpose,property))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(rental,interventions);

        when(rental.summary(access,property)).thenReturn(new HumanFeedbackSummary(
                UUID.randomUUID(),1,1,1,1,0,"INVALID_SOURCE"));
        assertThatThrownBy(()->service.evaluate(access,property))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(interventions);
    }

    private InterventionReview review(UUID id) {
        return new InterventionReview(id,workspace,property,UUID.randomUUID(),
                "guardian-policy","1.0","advisory-v1","OBLIGATIONS",
                "REVIEW_OBLIGATIONS",VitalStatus.AMBER,"ACKNOWLEDGED","Review requested",
                actor,now.minusSeconds(1800));
    }
    private InterventionOutcome outcome(UUID review,String direction,String level) {
        return new InterventionOutcome(UUID.randomUUID(),workspace,property,review,
                UUID.randomUUID(),level.equals("VERIFIED_WORK_ORDER")?UUID.randomUUID():null,
                VitalStatus.AMBER,VitalStatus.GREEN,direction,level,"Follow-up observation",
                actor,now);
    }
}
