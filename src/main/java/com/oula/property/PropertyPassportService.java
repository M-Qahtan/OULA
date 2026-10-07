package com.oula.property;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyPassportService {
    private static final Set<AccessPurpose> ALLOWED_PURPOSES = Set.of(
            AccessPurpose.PROPERTY_DECISION_SUPPORT,
            AccessPurpose.PROPERTY_MANAGEMENT
    );

    private final PropertyPassportRepository passports;
    private final PropertyStateSnapshotRepository snapshots;
    private final Clock clock = Clock.systemUTC();

    public PropertyPassportService(
            PropertyPassportRepository passports,
            PropertyStateSnapshotRepository snapshots
    ) {
        this.passports = passports;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public PropertyPassport get(AccessContext access, UUID propertyId) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(propertyId, "propertyId");
        if (!ALLOWED_PURPOSES.contains(access.purpose())) {
            throw new SecurityException("purpose is not authorized for Property Passport");
        }

        var asset = passports.asset(access.workspaceId(), propertyId);
        List<PropertyPassportFact> facts = passports.facts(propertyId);
        long verified = facts.stream()
                .filter(fact -> "VERIFIED".equals(fact.truthStatus()))
                .count();
        double coverage = facts.isEmpty() ? 0.0 : (double) verified / facts.size();

        PropertyStateSnapshot latestState = snapshots.latestRecorded(
                access.workspaceId(), propertyId
        );

        return new PropertyPassport(
                asset.propertyId(),
                asset.workspaceId(),
                asset.assetType(),
                asset.district(),
                asset.bedrooms(),
                asset.askingPrice(),
                facts,
                latestState,
                coverage,
                clock.instant()
        );
    }
}
