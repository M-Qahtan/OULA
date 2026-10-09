package com.oula.tenancy;

import com.oula.documents.VerifiedDocumentEvidenceService;
import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import com.oula.operations.CreateObligationCommand;
import com.oula.operations.PropertyManagementService;
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
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TenancyService {
    // Riyadh launch clock is explicit; future jurisdictions need per-place zone policy.
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Riyadh");
    private static final Set<String> RENEWALS = Set.of(
            "RENEWAL_REQUESTED","RENEWAL_DECLINED","RENEWAL_ACCEPTED","NEEDS_REVIEW"
    );
    private final TenancyRepository repository;
    private final VerifiedDocumentEvidenceService evidence;
    private final PropertyManagementService management;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final Clock clock = Clock.systemUTC();

    public TenancyService(TenancyRepository repository,
                          VerifiedDocumentEvidenceService evidence,
                          PropertyManagementService management,
                          AuditWriter audit, OutboxWriter outbox) {
        this.repository=repository;
        this.evidence=evidence;
        this.management=management;
        this.audit=audit;
        this.outbox=outbox;
    }

    @Transactional
    public RentalUnit registerUnit(AccessContext access, UUID propertyId,
                                   String unitCode, UUID correlationId) {
        human(access);
        Objects.requireNonNull(propertyId,"propertyId");
        requireText(unitCode,"unitCode");
        if (unitCode.length()>80) throw new IllegalArgumentException("unit code is too long");
        repository.requireProperty(access.workspaceId(),propertyId);
        RentalUnit u=new RentalUnit(UuidV7.next(),access.workspaceId(),propertyId,
                unitCode.trim(),"ACTIVE",access.actorId(),clock.instant());
        repository.insertUnit(u);
        emit(access,"TENANCY_UNIT_REGISTERED","tenancy.unit.registered.v1",
                "RentalUnit",u.id(),correlationId,
                Map.of("propertyId",propertyId,"unitCode",u.unitCode()));
        return u;
    }

    @Transactional
    public Lease createLease(AccessContext access, UUID unitId,
                             CreateLeaseCommand command, UUID correlationId) {
        human(access);
        Objects.requireNonNull(command,"command");
        Objects.requireNonNull(command.landlordPartyId(),"landlordPartyId");
        Objects.requireNonNull(command.tenantPartyId(),"tenantPartyId");
        Objects.requireNonNull(command.startOn(),"startOn");
        Objects.requireNonNull(command.endOn(),"endOn");
        Objects.requireNonNull(correlationId,"correlationId");
        if(command.landlordPartyId().equals(command.tenantPartyId()))
            throw new IllegalArgumentException("parties must be distinct");
        if (!command.endOn().isAfter(command.startOn()))
            throw new IllegalArgumentException("end date must follow start date");
        if (command.periodicRent()==null || command.periodicRent().signum()<=0)
            throw new IllegalArgumentException("periodicRent must be positive");
        if (command.periodicRent().scale()>4)
            throw new IllegalArgumentException("rent precision exceeds four decimal places");
        if (!List.of(1,3,12).contains(command.rentEveryMonths()))
            throw new IllegalArgumentException("rent period must be 1, 3 or 12 months");
        String currency=Currency.getInstance(command.currency()).getCurrencyCode();
        int periods=periodCount(command.startOn(),command.endOn(),command.rentEveryMonths());

        RentalUnit unit=repository.unit(access.workspaceId(),unitId,false);
        if (!"ACTIVE".equals(unit.status()))
            throw new IllegalStateException("unit is not active");

        Lease l=new Lease(UuidV7.next(),access.workspaceId(),unitId,
                command.landlordPartyId(),command.tenantPartyId(),
                command.startOn(),command.endOn(),command.periodicRent(),currency,
                command.rentEveryMonths(),"DRAFT",null,null,null,null,null,
                access.actorId(),clock.instant(),0);
        repository.insertLease(l);
        emit(access,"TENANCY_LEASE_DRAFTED","tenancy.lease.drafted.v1",
                "Lease",l.id(),correlationId,
                Map.of("unitId",unitId,"periods",periods,"status","DRAFT"));
        return l;
    }

    @Transactional
    public Lease sign(AccessContext access, UUID leaseId,
                      UUID contractEvidenceId, UUID correlationId) {
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!"DRAFT".equals(l.status()))
            throw new IllegalStateException("only a draft can be signed");
        // Lock unit to serialize competing sign operations. DB exclusion is final guard.
        repository.unit(access.workspaceId(),l.unitId(),true);
        if(repository.hasOverlappingCommittedLease(l))
            throw new IllegalStateException("signed lease period overlaps another committed lease");
        evidence.requireVerified(access.workspaceId(),contractEvidenceId,"LEASE_CONTRACT");
        if(!l.endOn().isAfter(today()))
            throw new IllegalStateException("lease term has already elapsed");
        Lease signed=repository.sign(l,contractEvidenceId,clock.instant());
        emit(access,"TENANCY_LEASE_SIGNED","tenancy.lease.signed.v1",
                "Lease",signed.id(),correlationId,
                Map.of("unitId",signed.unitId(),"evidenceId",contractEvidenceId));
        return signed;
    }

    @Transactional
    public Lease activate(AccessContext access, UUID leaseId, UUID correlationId) {
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!"SIGNED".equals(l.status()))
            throw new IllegalStateException("only a signed lease can be activated");
        if(today().isBefore(l.startOn()) || !today().isBefore(l.endOn()))
            throw new IllegalStateException("lease is outside its active term");
        RentalUnit unit=repository.unit(access.workspaceId(),l.unitId(),false);
        // No Guardian obligation can be silently created without active management authority.
        management.overview(access,unit.propertyId());
        Lease active=repository.activate(l,clock.instant());
        List<RentInstallment> dues=new ArrayList<>();
        int periods=periodCount(l.startOn(),l.endOn(),l.rentEveryMonths());
        for(int i=0;i<periods;i++){
            dues.add(new RentInstallment(UuidV7.next(),access.workspaceId(),
                    l.id(),l.startOn().plusMonths((long)i*l.rentEveryMonths()),
                    l.periodicRent(),l.currency(),"SCHEDULED",clock.instant()));
        }
        repository.insertInstallments(dues);
        // Review due 60 days before contractual expiry, not proof of government notice.
        Instant reviewAt=l.endOn().minusDays(60).atStartOfDay(LOCAL_ZONE).toInstant();
        management.createObligation(access,unit.propertyId(),new CreateObligationCommand(
                "LEASE_RENEWAL_REVIEW",
                "Review lease renewal for unit "+unit.unitCode(),
                reviewAt,"HIGH","TENANCY_LEASE","lease:"+l.id()+":renewal"
        ),correlationId);
        emit(access,"TENANCY_LEASE_ACTIVATED","tenancy.lease.activated.v1",
                "Lease",active.id(),correlationId,
                Map.of("unitId",l.unitId(),"installmentCount",dues.size()));
        return active;
    }

    @Transactional
    public Occupancy checkIn(AccessContext access, UUID leaseId,
                             UUID handoverEvidenceId, UUID correlationId) {
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!"ACTIVE".equals(l.status()) ||
                today().isBefore(l.startOn()) || !today().isBefore(l.endOn()))
            throw new IllegalStateException("active in-term lease is required");
        if(repository.activeOccupancy(access.workspaceId(),leaseId)!=null)
            throw new IllegalStateException("lease is already occupied");
        evidence.requireVerified(access.workspaceId(),handoverEvidenceId,"HANDOVER_RECORD");
        Occupancy occ=new Occupancy(UuidV7.next(),access.workspaceId(),
                l.unitId(),l.id(),clock.instant(),handoverEvidenceId,
                null,null,access.actorId());
        repository.checkIn(occ);
        emit(access,"TENANCY_OCCUPANCY_STARTED","tenancy.occupancy.started.v1",
                "Occupancy",occ.id(),correlationId,
                Map.of("leaseId",l.id(),"unitId",l.unitId(),"evidenceId",handoverEvidenceId));
        return occ;
    }

    @Transactional
    public Occupancy checkOut(AccessContext access, UUID leaseId,
                              UUID returnEvidenceId, UUID correlationId) {
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        Occupancy occ=repository.activeOccupancy(access.workspaceId(),leaseId);
        if(occ==null) throw new IllegalStateException("no active occupancy to close");
        evidence.requireVerified(access.workspaceId(),returnEvidenceId,"HANDOVER_RETURN");
        Occupancy closed=repository.checkOut(occ,returnEvidenceId,clock.instant());
        emit(access,"TENANCY_OCCUPANCY_ENDED","tenancy.occupancy.ended.v1",
                "Occupancy",closed.id(),correlationId,
                Map.of("leaseId",l.id(),"unitId",l.unitId(),"evidenceId",returnEvidenceId));
        return closed;
    }

    @Transactional
    public Lease endLease(AccessContext access, UUID leaseId,
                          UUID terminationEvidenceId, UUID correlationId) {
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!"ACTIVE".equals(l.status()))
            throw new IllegalStateException("only active lease may end");
        if(repository.activeOccupancy(access.workspaceId(),leaseId)!=null)
            throw new IllegalStateException("check out occupancy before ending lease");
        evidence.requireVerified(access.workspaceId(),terminationEvidenceId,"LEASE_TERMINATION");
        Lease ended=repository.end(l,terminationEvidenceId,clock.instant());
        emit(access,"TENANCY_LEASE_ENDED","tenancy.lease.ended.v1",
                "Lease",ended.id(),correlationId,
                Map.of("unitId",l.unitId(),"terminationEvidenceId",terminationEvidenceId));
        return ended;
    }

    @Transactional
    public Lease cancelDraft(AccessContext access,UUID leaseId,UUID correlationId){
        human(access);
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!"DRAFT".equals(l.status()))
            throw new IllegalStateException("only draft leases can be cancelled");
        Lease result=repository.cancelDraft(l);
        emit(access,"TENANCY_LEASE_CANCELLED","tenancy.lease.cancelled.v1",
                "Lease",result.id(),correlationId,Map.of("unitId",result.unitId()));
        return result;
    }

    @Transactional
    public RenewalDecision recordRenewal(AccessContext access, UUID leaseId,
                                         String decision, String rationale, UUID evidenceId,
                                         UUID correlationId) {
        human(access);
        requireText(rationale,"rationale");
        if(rationale.length()>2000) throw new IllegalArgumentException("rationale is too long");
        if(!RENEWALS.contains(decision)) throw new IllegalArgumentException("unknown renewal decision");
        Lease l=repository.lease(access.workspaceId(),leaseId,true);
        if(!Set.of("SIGNED","ACTIVE").contains(l.status()))
            throw new IllegalStateException("lease does not accept renewal decisions");
        if (Set.of("RENEWAL_ACCEPTED","RENEWAL_DECLINED").contains(decision)) {
            evidence.requireVerified(access.workspaceId(),evidenceId,"RENEWAL_DECISION");
        } else if(evidenceId!=null) {
            evidence.requireVerified(access.workspaceId(),evidenceId,"RENEWAL_DECISION");
        }
        RenewalDecision record=new RenewalDecision(UuidV7.next(),access.workspaceId(),leaseId,
                decision,rationale,evidenceId,access.actorId(),clock.instant());
        repository.insertRenewal(record);
        emit(access,"TENANCY_RENEWAL_DECISION_RECORDED","tenancy.renewal.recorded.v1",
                "RenewalDecision",record.id(),correlationId,
                Map.of("leaseId",leaseId,"decision",decision));
        // An accepted intention never extends the existing signed term.
        return record;
    }

    @Transactional(readOnly=true)
    public Lease lease(AccessContext access,UUID leaseId) {
        human(access);
        return repository.lease(access.workspaceId(),leaseId,false);
    }
    @Transactional(readOnly=true)
    public List<Lease> leasesForUnit(AccessContext access,UUID unitId) {
        human(access);
        return repository.leasesForUnit(access.workspaceId(),unitId);
    }
    @Transactional(readOnly=true)
    public List<RentInstallment> rentSchedule(AccessContext access,UUID leaseId) {
        human(access);
        return repository.installments(access.workspaceId(),leaseId);
    }
    @Transactional(readOnly=true)
    public List<RenewalDecision> renewalHistory(AccessContext access,UUID leaseId) {
        human(access);
        return repository.renewals(access.workspaceId(),leaseId);
    }

    private int periodCount(LocalDate start,LocalDate end,int months) {
        for(int i=1;i<=120;i++){
            LocalDate next=start.plusMonths((long)i*months);
            if(next.equals(end)) return i;
            if(next.isAfter(end)) break;
        }
        throw new IllegalArgumentException(
                "lease term must be an exact number of rent periods (max 120)");
    }
    private LocalDate today(){return LocalDate.now(clock.withZone(LOCAL_ZONE));}
    private void human(AccessContext access){
        Objects.requireNonNull(access,"access");
        if(access.purpose()!=AccessPurpose.PROPERTY_MANAGEMENT)
            throw new SecurityException("PROPERTY_MANAGEMENT purpose is required");
    }
    private void requireText(String v,String label){
        if(v==null||v.isBlank()) throw new IllegalArgumentException(label+" is required");
    }
    private void emit(AccessContext access,String auditAction,String eventType,
                      String aggregateType,UUID id,UUID correlationId,Map<String,?> details) {
        audit.append(access.workspaceId(),access.actorId(),access.subject(),
                access.purpose().name(),auditAction,aggregateType,id,correlationId,details);
        outbox.append(eventType,aggregateType,id,access.workspaceId(),
                correlationId,correlationId,details);
    }
}
