package com.oula.tenancy;

import com.oula.documents.VerifiedDocumentEvidenceService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.platform.UuidV7;
import com.oula.platform.audit.AuditWriter;
import com.oula.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RentalFinancialService {
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private final RentalFinancialRepository finance;
    private final TenancyRepository tenancy;
    private final VerifiedDocumentEvidenceService evidence;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock=Clock.systemUTC();

    public RentalFinancialService(RentalFinancialRepository finance,TenancyRepository tenancy,
                                  VerifiedDocumentEvidenceService evidence,AuditWriter audit,
                                  OutboxWriter outbox) {
        this.finance=finance;this.tenancy=tenancy;this.evidence=evidence;
        this.audit=audit;this.outbox=outbox;
    }

    @Transactional
    public RentEvidenceEntry recordDocumentaryReceipt(
            AccessContext access,UUID leaseId,UUID installmentId,BigDecimal amount,
            UUID receiptEvidenceId,String externalReference,String note,UUID correlationId) {
        requireManagement(access);
        Objects.requireNonNull(correlationId,"correlationId");
        positiveMoney(amount);
        String reference=reference(externalReference);
        requireNote(note);
        Lease lease=tenancy.lease(access.workspaceId(),leaseId,true);
        if(!Set.of("ACTIVE","ENDED").contains(lease.status()))
            throw new IllegalStateException("only active/ended lease installments accept receipts");
        RentInstallment installment=finance.lockedInstallment(access.workspaceId(),leaseId,installmentId);
        if(!installment.currency().equals(lease.currency()))
            throw new IllegalStateException("installment and lease currency do not match");
        BigDecimal net=finance.netDocumentaryReceipts(access.workspaceId(),leaseId,installmentId);
        if(net.add(amount).compareTo(installment.amount())>0)
            throw new IllegalStateException("documentary allocation exceeds contractual installment");
        evidence.requireVerified(access.workspaceId(),receiptEvidenceId,"RENT_RECEIPT");
        RentEvidenceEntry entry=new RentEvidenceEntry(
                UuidV7.next(),access.workspaceId(),leaseId,installmentId,
                "RECEIPT",amount,lease.currency(),receiptEvidenceId,reference,
                null,note.strip(),access.actorId(),clock.instant());
        finance.append(entry);
        emit(access,"TENANCY_RENT_RECEIPT_EVIDENCE_RECORDED","tenancy.rent.receipt_recorded.v1",
                entry,correlationId);
        return entry;
    }

    @Transactional
    public RentEvidenceEntry reverseDocumentaryReceipt(
            AccessContext access,UUID leaseId,UUID receiptId,UUID reversalEvidenceId,
            String rationale,UUID correlationId) {
        requireManagement(access);
        Objects.requireNonNull(correlationId,"correlationId");
        requireNote(rationale);
        RentEvidenceEntry receipt=finance.entry(access.workspaceId(),leaseId,receiptId);
        if(!"RECEIPT".equals(receipt.entryType()))
            throw new IllegalStateException("only an original receipt can be reversed");
        // Lock installment to serialize all concurrent allocations and reversals.
        finance.lockedInstallment(access.workspaceId(),leaseId,receipt.installmentId());
        if(finance.hasReversal(receipt.id()))
            throw new IllegalStateException("receipt has already been reversed");
        evidence.requireVerified(access.workspaceId(),reversalEvidenceId,"RENT_RECEIPT_REVERSAL");
        RentEvidenceEntry reversal=new RentEvidenceEntry(
                UuidV7.next(),access.workspaceId(),leaseId,receipt.installmentId(),
                "REVERSAL",receipt.amount(),receipt.currency(),reversalEvidenceId,
                null,receipt.id(),rationale.strip(),access.actorId(),clock.instant());
        finance.append(reversal);
        emit(access,"TENANCY_RENT_RECEIPT_REVERSED","tenancy.rent.receipt_reversed.v1",
                reversal,correlationId);
        return reversal;
    }

    @Transactional(readOnly=true)
    public RentalFinancialSummary financialSummary(
            AccessContext access,UUID leaseId,LocalDate asOfDate) {
        requireManagement(access);
        Objects.requireNonNull(asOfDate,"asOfDate");
        if(asOfDate.isAfter(LocalDate.now(clock.withZone(RIYADH))))
            throw new IllegalArgumentException("future financial observations are not supported");
        Lease lease=tenancy.lease(access.workspaceId(),leaseId,false);
        List<RentEvidencePosition> lines=finance.positions(access.workspaceId(),leaseId,asOfDate);
        BigDecimal contractual=BigDecimal.ZERO,net=BigDecimal.ZERO,uncovered=BigDecimal.ZERO;
        int overdue=0;
        for(RentEvidencePosition line:lines){
            contractual=contractual.add(line.contractualDue());
            net=net.add(line.documentaryNetReceived());
            if(!line.dueOn().isAfter(asOfDate)) {
                uncovered=uncovered.add(line.documentaryGap());
                if(line.dueOn().isBefore(asOfDate) && line.documentaryGap().signum()>0) overdue++;
            }
        }
        return new RentalFinancialSummary(leaseId,asOfDate,lease.currency(),contractual,
                net,uncovered,overdue,List.copyOf(lines));
    }

    @Transactional(readOnly=true)
    public PropertyOccupancyInsight occupancyInsight(
            AccessContext access,UUID propertyId,Instant asOf) {
        requireManagement(access);
        Objects.requireNonNull(asOf,"asOf");
        if(asOf.isAfter(clock.instant()))
            throw new IllegalArgumentException("future physical occupancy cannot be observed");
        tenancy.requireProperty(access.workspaceId(),propertyId);
        return finance.occupancyInsight(access.workspaceId(),propertyId,asOf);
    }

    private void requireManagement(AccessContext access) {
        Objects.requireNonNull(access,"access");
        if(access.purpose()!=AccessPurpose.PROPERTY_MANAGEMENT)
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
    }
    private void positiveMoney(BigDecimal amount) {
        if(amount==null||amount.signum()<=0||amount.scale()>4)
            throw new IllegalArgumentException("amount must be positive with <=4 decimal places");
    }
    private String reference(String value) {
        if(value==null||value.isBlank())return null;
        if(value.strip().length()>255) throw new IllegalArgumentException("reference too long");
        return value.strip();
    }
    private void requireNote(String value) {
        if(value==null||value.isBlank()||value.length()>1000)
            throw new IllegalArgumentException("nonblank note required (max 1000 chars)");
    }
    private void emit(AccessContext access,String auditAction,String eventType,
                      RentEvidenceEntry entry,UUID correlationId) {
        // No PII or document content in events; only trace references.
        Map<String,Object> details=Map.of(
                "leaseId",entry.leaseId(),"installmentId",entry.installmentId(),
                "entryType",entry.entryType(),"amount",entry.amount(),
                "currency",entry.currency(),"evidenceId",entry.evidenceId());
        audit.append(access.workspaceId(),access.actorId(),access.subject(),
                access.purpose().name(),auditAction,"RentEvidenceEntry",entry.id(),correlationId,details);
        outbox.append(eventType,"RentEvidenceEntry",entry.id(),access.workspaceId(),
                correlationId,correlationId,details);
    }
}
