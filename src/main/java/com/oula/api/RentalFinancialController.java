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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
class RentalFinancialController {
    private static final ZoneId RIYADH=ZoneId.of("Asia/Riyadh");
    private final WorkspacePurposeAuthorizer authorizer;
    private final IdempotencyService idempotency;
    private final RentalFinancialService finance;

    RentalFinancialController(WorkspacePurposeAuthorizer authorizer,
                              IdempotencyService idempotency,
                              RentalFinancialService finance) {
        this.authorizer=authorizer;
        this.idempotency=idempotency;
        this.finance=finance;
    }

    @PostMapping("/v1/leases/{leaseId}/installments/{installmentId}/receipt-evidence")
    ResponseEntity<RentEvidenceEntry> receipt(
            @PathVariable UUID leaseId,@PathVariable UUID installmentId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReceiptRequest req,Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.finance.record");
        String op="tenancy.rent.receipt.record.v1";
        return execute(w,key,op,leaseId,installmentId,req,
                ()->finance.recordDocumentaryReceipt(access,leaseId,installmentId,
                        req.amount(),req.evidenceId(),req.externalReference(),
                        req.note(),DeterministicUuid.from(op,w,key)));
    }

    @PostMapping("/v1/leases/{leaseId}/receipt-evidence/{receiptId}/reverse")
    ResponseEntity<RentEvidenceEntry> reverse(
            @PathVariable UUID leaseId,@PathVariable UUID receiptId,
            @RequestHeader("X-OULA-Workspace-ID") UUID w,
            @RequestHeader("X-OULA-Purpose") String purpose,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReverseRequest req,Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.finance.reverse");
        String op="tenancy.rent.receipt.reverse.v1";
        return execute(w,key,op,leaseId,receiptId,req,
                ()->finance.reverseDocumentaryReceipt(access,leaseId,receiptId,
                        req.reversalEvidenceId(),req.rationale(),
                        DeterministicUuid.from(op,w,key)));
    }

    @GetMapping("/v1/leases/{leaseId}/financial-evidence")
    RentalFinancialSummary summary(@PathVariable UUID leaseId,
                                  @RequestHeader("X-OULA-Workspace-ID") UUID w,
                                  @RequestHeader("X-OULA-Purpose") String purpose,
                                  @RequestParam(required=false) LocalDate asOf,
                                  Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.finance.read");
        LocalDate date=asOf==null?LocalDate.now(RIYADH):asOf;
        return finance.financialSummary(access,leaseId,date);
    }

    @GetMapping("/v1/properties/{propertyId}/occupancy-insight")
    PropertyOccupancyInsight occupancy(@PathVariable UUID propertyId,
                                       @RequestHeader("X-OULA-Workspace-ID") UUID w,
                                       @RequestHeader("X-OULA-Purpose") String purpose,
                                       @RequestParam(required=false) Instant asOf,
                                       Authentication auth) {
        AccessContext access=authorize(auth,w,purpose,"oula.tenancy.occupancy.read");
        return finance.occupancyInsight(access,propertyId,asOf==null?Instant.now():asOf);
    }

    private AccessContext authorize(Authentication auth,UUID w,String purpose,String scope){
        return authorizer.require(auth,w,purpose,AccessPurpose.PROPERTY_MANAGEMENT,scope);
    }

    private ResponseEntity<RentEvidenceEntry> execute(
            UUID w,String key,String op,UUID leaseId,UUID reference,Object request,
            Supplier<RentEvidenceEntry> action) {
        String fingerprint=RequestFingerprint.sha256(op,w,leaseId,reference,request);
        var result=idempotency.execute(w,key,op,fingerprint,
                HttpStatus.CREATED.value(),RentEvidenceEntry.class,action);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(result.value());
    }

    record ReceiptRequest(
            @NotNull @DecimalMin("0.0001") BigDecimal amount,
            @NotNull UUID evidenceId,
            @Size(max=255) String externalReference,
            @NotBlank @Size(max=1000) String note
    ) {}
    record ReverseRequest(
            @NotNull UUID reversalEvidenceId,
            @NotBlank @Size(max=1000) String rationale
    ) {}
}
