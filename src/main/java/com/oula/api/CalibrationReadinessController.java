package com.oula.api;

import com.oula.evaluation.CalibrationReadinessReport;
import com.oula.evaluation.CalibrationReadinessService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Read-only scientific calibration readiness. No training or model mutation.
 */
@RestController
class CalibrationReadinessController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final CalibrationReadinessService readiness;

    CalibrationReadinessController(WorkspacePurposeAuthorizer authorizer,
                                   CalibrationReadinessService readiness) {
        this.authorizer = authorizer;
        this.readiness = readiness;
    }

    @GetMapping("/v1/intelligence/models/{modelVersionId}/calibration-readiness")
    ResponseEntity<CalibrationReadinessReport> report(
            @PathVariable UUID modelVersionId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication authentication) {
        AccessContext access = authorizer.require(
                authentication,
                workspaceId,
                purpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                "oula.intelligence.read");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(readiness.evaluate(access, modelVersionId));
    }
}
