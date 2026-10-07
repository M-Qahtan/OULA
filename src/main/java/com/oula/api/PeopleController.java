package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.people.*;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
class PeopleController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final PeopleApplicationService people;

    PeopleController(
            WorkspacePurposeAuthorizer authorizer,
            IdempotencyService idempotency,
            PeopleApplicationService people
    ) {
        this.authorizer = authorizer;
        this.idempotency = idempotency;
        this.people = people;
    }

    @PostMapping("/people/me")
    ResponseEntity<PersonView> provisionSelf(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PersonRequest request,
            Authentication authentication
    ) {
        String operation = "people.person.provision.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.people.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.displayName()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), PersonView.class,
                () -> people.provisionSelf(access, request.displayName(), correlationId)
        );
        return created(result.value(), result.replayed());
    }

    @PostMapping("/households")
    ResponseEntity<HouseholdView> createHousehold(
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody HouseholdRequest request,
            Authentication authentication
    ) {
        String operation = "people.household.create.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.people.write"
        );
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, request.personId(), request.name()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), HouseholdView.class,
                () -> people.createHousehold(
                        access, request.personId(), request.name(), correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @PostMapping("/people/{personId}/life-profile-snapshots")
    ResponseEntity<LifeGraphSnapshot> recordLifeProfile(
            @PathVariable UUID personId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifeProfileRequest request,
            Authentication authentication
    ) {
        String operation = "people.life_profile.record.v1";
        AccessContext access = authorize(
                authentication, workspaceId, requestedPurpose, "oula.people.write"
        );
        List<UUID> anchors = sorted(request.mobilityAnchors());
        String fingerprint = RequestFingerprint.sha256(
                operation, workspaceId, personId, request.householdId(),
                request.householdSize(), request.maxHousingBudget(), anchors,
                request.preferences(), request.constraints(), request.effectiveAt()
        );
        UUID correlationId = DeterministicUuid.from(operation, workspaceId, idempotencyKey);
        var result = idempotency.execute(
                workspaceId, idempotencyKey, operation, fingerprint,
                HttpStatus.CREATED.value(), LifeGraphSnapshot.class,
                () -> people.recordLifeProfile(
                        access,
                        personId,
                        new RecordLifeProfileCommand(
                                request.householdId(),
                                request.householdSize(),
                                request.maxHousingBudget(),
                                Set.copyOf(anchors),
                                request.preferences(),
                                request.constraints(),
                                request.effectiveAt()
                        ),
                        correlationId
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.value());
    }

    @GetMapping("/people/{personId}/life-profile")
    LifeGraphSnapshot latestLifeProfile(
            @PathVariable UUID personId,
            @RequestHeader("X-OULA-Workspace-ID") UUID workspaceId,
            @RequestHeader("X-OULA-Purpose") String requestedPurpose,
            Authentication authentication
    ) {
        return people.latestLifeProfile(
                authorize(authentication, workspaceId, requestedPurpose, "oula.people.read"),
                personId
        );
    }

    private AccessContext authorize(
            Authentication authentication,
            UUID workspaceId,
            String requestedPurpose,
            String scope
    ) {
        return authorizer.require(
                authentication,
                workspaceId,
                requestedPurpose,
                AccessPurpose.PROPERTY_DECISION_SUPPORT,
                scope
        );
    }

    private ResponseEntity<PersonView> created(PersonView value, boolean replayed) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(value);
    }

    private List<UUID> sorted(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<UUID> copy = new ArrayList<>(ids);
        copy.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(copy);
    }

    record PersonRequest(@NotBlank @Size(max = 255) String displayName) {}

    record HouseholdRequest(UUID personId, @Size(max = 200) String name) {}

    record LifeProfileRequest(
            UUID householdId,
            @Min(1) int householdSize,
            @DecimalMin("0.01") BigDecimal maxHousingBudget,
            List<UUID> mobilityAnchors,
            Map<String, Object> preferences,
            Map<String, Object> constraints,
            Instant effectiveAt
    ) {}
}
