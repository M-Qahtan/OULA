package com.oula.api;

import com.oula.evaluation.DecisionEvaluationReport;
import com.oula.evaluation.DecisionEvaluationService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Purpose-bound read-only evaluation. No mutation or model training endpoints. */
@RestController
class DecisionEvaluationController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final DecisionEvaluationService evaluation;

    DecisionEvaluationController(WorkspacePurposeAuthorizer authorizer,
                                 DecisionEvaluationService evaluation) {
        this.authorizer=authorizer;
        this.evaluation=evaluation;
    }

    @GetMapping("/v1/properties/{propertyId}/decision-evaluation")
    ResponseEntity<DecisionEvaluationReport> report(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        AccessContext access=authorizer.require(authentication,workspaceId,purpose,
                AccessPurpose.PROPERTY_MANAGEMENT,"oula.property.decision-evaluation.read");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(evaluation.evaluate(access,propertyId));
    }
}
