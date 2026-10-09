package com.oula.api;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.iam.WorkspacePurposeAuthorizer;
import com.oula.platform.DeterministicUuid;
import com.oula.platform.RequestFingerprint;
import com.oula.platform.idempotency.IdempotencyService;
import com.oula.tenancy.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
class TenancyController {
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final TenancyService tenancy;

    TenancyController(WorkspacePurposeAuthorizer authorizer,
                      IdempotencyService idempotency, TenancyService tenancy) {
        this.authorizer=authorizer;
        this.idempotency=idempotency;
        this.tenancy=tenancy;
    }

    @PostMapping("/v1/properties/{propertyId}/units")
    ResponseEntity<RentalUnit> registerUnit(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RegisterUnitRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.write");
        return execute(w,key,"tenancy.unit.register.v1",propertyId,
                req.unitCode(),HttpStatus.CREATED,RentalUnit.class,
                () -> tenancy.registerUnit(access,propertyId,req.unitCode(),
                        correlation("tenancy.unit.register.v1",w,key)));
    }

    @PostMapping("/v1/units/{unitId}/leases")
    ResponseEntity<Lease> createLease(
            @PathVariable UUID unitId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody LeaseRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.write");
        String op="tenancy.lease.create.v1";
        return execute(w,key,op,unitId,req,HttpStatus.CREATED,Lease.class,
                ()->tenancy.createLease(access,unitId,
                        new CreateLeaseCommand(req.landlordPartyId(),req.tenantPartyId(),
                                req.startOn(),req.endOn(),req.periodicRent(),
                                req.currency(),req.rentEveryMonths()),
                        correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/sign")
    ResponseEntity<Lease> sign(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EvidenceRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.sign");
        String op="tenancy.lease.sign.v1";
        return execute(w,key,op,leaseId,req,HttpStatus.OK,Lease.class,
                ()->tenancy.sign(access,leaseId,req.evidenceId(),correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/activate")
    ResponseEntity<Lease> activate(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.activate");
        String op="tenancy.lease.activate.v1";
        return execute(w,key,op,leaseId,"activate",HttpStatus.OK,Lease.class,
                ()->tenancy.activate(access,leaseId,correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/check-in")
    ResponseEntity<Occupancy> checkIn(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EvidenceRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.occupancy.write");
        String op="tenancy.occupancy.check_in.v1";
        return execute(w,key,op,leaseId,req,HttpStatus.CREATED,Occupancy.class,
                ()->tenancy.checkIn(access,leaseId,req.evidenceId(),correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/check-out")
    ResponseEntity<Occupancy> checkOut(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EvidenceRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.occupancy.write");
        String op="tenancy.occupancy.check_out.v1";
        return execute(w,key,op,leaseId,req,HttpStatus.OK,Occupancy.class,
                ()->tenancy.checkOut(access,leaseId,req.evidenceId(),correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/end")
    ResponseEntity<Lease> endLease(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EvidenceRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.end");
        String op="tenancy.lease.end.v1";
        return execute(w,key,op,leaseId,req,HttpStatus.OK,Lease.class,
                ()->tenancy.endLease(access,leaseId,req.evidenceId(),correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/cancel-draft")
    ResponseEntity<Lease> cancelDraft(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.write");
        String op="tenancy.lease.cancel_draft.v1";
        return execute(w,key,op,leaseId,"cancel",HttpStatus.OK,Lease.class,
                ()->tenancy.cancelDraft(access,leaseId,correlation(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/renewal-decisions")
    ResponseEntity<RenewalDecision> renewal(
            @PathVariable UUID leaseId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RenewalRequest req,
            Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.renewal.write");
        String op="tenancy.renewal.record.v1";
        return execute(w,key,op,leaseId,req,HttpStatus.CREATED,RenewalDecision.class,
                ()->tenancy.recordRenewal(access,leaseId,req.decision(),
                        req.rationale(),req.evidenceId(),correlation(op,w,key)));
    }

    @GetMapping("/v1/properties/{propertyId}/occupancy")
    List<UnitOccupancyView> propertyOccupancy(
            @PathVariable UUID propertyId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            Authentication auth) {
        return tenancy.occupancyByProperty(
                authorize(auth,w,purpose,"oula.tenancy.read"),propertyId);
    }

    @GetMapping("/v1/leases/{leaseId}")
    Lease getLease(@PathVariable UUID leaseId,
                   @RequestHeader("X-OULA-Workspace-ID") UUID w,
                   @RequestHeader("X-OULA-Purpose") String purpose,
                   Authentication auth) {
        return tenancy.lease(authorize(auth,w,purpose,"oula.tenancy.read"),leaseId);
    }

    @GetMapping("/v1/units/{unitId}/leases")
    List<Lease> listLeases(@PathVariable UUID unitId,
                           @RequestHeader("X-OULA-Workspace-ID") UUID w,
                           @RequestHeader("X-OULA-Purpose") String purpose,
                           Authentication auth) {
        return tenancy.leasesForUnit(authorize(auth,w,purpose,"oula.tenancy.read"),unitId);
    }

    @GetMapping("/v1/leases/{leaseId}/rent-schedule")
    List<RentInstallment> rentSchedule(@PathVariable UUID leaseId,
                                      @RequestHeader("X-OULA-Workspace-ID") UUID w,
                                      @RequestHeader("X-OULA-Purpose") String purpose,
                                      Authentication auth) {
        return tenancy.rentSchedule(authorize(auth,w,purpose,"oula.tenancy.read"),leaseId);
    }

    @GetMapping("/v1/leases/{leaseId}/renewal-decisions")
    List<RenewalDecision> renewals(@PathVariable UUID leaseId,
                                   @RequestHeader("X-OULA-Workspace-ID") UUID w,
                                   @RequestHeader("X-OULA-Purpose") String purpose,
                                   Authentication auth) {
        return tenancy.renewalHistory(authorize(auth,w,purpose,"oula.tenancy.read"),leaseId);
    }

    private AccessContext authorize(Authentication auth,UUID workspace,String purpose,String scope){
        return authorizer.require(auth,workspace,purpose,AccessPurpose.PROPERTY_MANAGEMENT,scope);
    }

    private UUID correlation(String operation,UUID workspace,String key){
        return DeterministicUuid.from(operation,workspace,key);
    }

    private <T> ResponseEntity<T> execute(
            UUID w,String key,String operation,Object resource,Object payload,
            HttpStatus status,Class<T> type,Supplier<T> action) {
        String hash=RequestFingerprint.sha256(operation,w,resource,payload);
        var result=idempotency.execute(w,key,operation,hash,status.value(),type,action);
        return ResponseEntity.status(status)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(result.value());
    }

    record RegisterUnitRequest(@NotBlank @Size(max=80) String unitCode) {}
    record EvidenceRequest(@NotNull UUID evidenceId) {}
    record LeaseRequest(
            @NotNull UUID landlordPartyId,
            @NotNull UUID tenantPartyId,
            @NotNull LocalDate startOn,
            @NotNull LocalDate endOn,
            @NotNull @DecimalMin("0.0001") BigDecimal periodicRent,
            @NotBlank @Size(min=3,max=3) String currency,
            @Min(1) int rentEveryMonths
    ) {}
    record RenewalRequest(
            @NotBlank String decision,
            @NotBlank @Size(max=2000) String rationale,
            UUID evidenceId
    ) {}
}
