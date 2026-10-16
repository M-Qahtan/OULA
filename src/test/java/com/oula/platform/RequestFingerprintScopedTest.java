package com.oula.platform;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestFingerprintScopedTest {
    private String scoped(AccessContext access, String requestHash) {
        if (access == null) {
            return RequestFingerprint.scoped(null, null, null, requestHash);
        }
        return RequestFingerprint.scoped(access.workspaceId(), access.actorId(),
                access.purpose() == null ? null : access.purpose().name(), requestHash);
    }

    @Test
    void sameActorPurposeWorkspaceAndRequestProduceStableFingerprint() {
        UUID workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        var access = new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        var tokenRenewal = new AccessContext(actor, "renewed-subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        String hash = RequestFingerprint.sha256("property.intake.v1", workspace, "Riyadh");
        assertThat(scoped(access, hash))
                .isEqualTo(scoped(tokenRenewal, hash));
    }

    @Test
    void replayChangesWhenActorPurposeWorkspaceOrPayloadChanges() {
        UUID workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        var access = new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        String hash = RequestFingerprint.sha256("property.intake.v1", workspace, "Riyadh");
        String baseline = scoped(access, hash);
        assertThat(scoped(
                new AccessContext(UUID.randomUUID(), "other", workspace, AccessPurpose.PROPERTY_MANAGEMENT), hash))
                .isNotEqualTo(baseline);
        assertThat(scoped(
                new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_DECISION_SUPPORT), hash))
                .isNotEqualTo(baseline);
        assertThat(scoped(
                new AccessContext(actor, "subject", UUID.randomUUID(), AccessPurpose.PROPERTY_MANAGEMENT), hash))
                .isNotEqualTo(baseline);
        assertThat(scoped(access, RequestFingerprint.sha256("different payload")))
                .isNotEqualTo(baseline);
        assertThat(scoped(access, hash)).isNotEqualTo(hash);
    }

    @Test
    void missingAuthorityNeverResultsInUnscopedFallback() {
        assertThatThrownBy(() -> scoped(null, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoped(
                new AccessContext(null, "subject", UUID.randomUUID(), AccessPurpose.PROPERTY_MANAGEMENT), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
