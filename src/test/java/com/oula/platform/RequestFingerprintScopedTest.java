package com.oula.platform;

import com.oula.iam.AccessContext;
import com.oula.iam.AccessPurpose;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestFingerprintScopedTest {
    @Test
    void sameActorPurposeWorkspaceAndRequestProduceStableFingerprint() {
        UUID workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        var access = new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        var tokenRenewal = new AccessContext(actor, "renewed-subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        String hash = RequestFingerprint.sha256("property.intake.v1", workspace, "Riyadh");
        assertThat(RequestFingerprint.scoped(access, hash))
                .isEqualTo(RequestFingerprint.scoped(tokenRenewal, hash));
    }

    @Test
    void replayChangesWhenActorPurposeWorkspaceOrPayloadChanges() {
        UUID workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        var access = new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_MANAGEMENT);
        String hash = RequestFingerprint.sha256("property.intake.v1", workspace, "Riyadh");
        String baseline = RequestFingerprint.scoped(access, hash);
        assertThat(RequestFingerprint.scoped(
                new AccessContext(UUID.randomUUID(), "other", workspace, AccessPurpose.PROPERTY_MANAGEMENT), hash))
                .isNotEqualTo(baseline);
        assertThat(RequestFingerprint.scoped(
                new AccessContext(actor, "subject", workspace, AccessPurpose.PROPERTY_DECISION_SUPPORT), hash))
                .isNotEqualTo(baseline);
        assertThat(RequestFingerprint.scoped(
                new AccessContext(actor, "subject", UUID.randomUUID(), AccessPurpose.PROPERTY_MANAGEMENT), hash))
                .isNotEqualTo(baseline);
        assertThat(RequestFingerprint.scoped(access, RequestFingerprint.sha256("different payload")))
                .isNotEqualTo(baseline);
        assertThat(RequestFingerprint.scoped(access, hash)).isNotEqualTo(hash);
    }

    @Test
    void missingAuthorityNeverResultsInUnscopedFallback() {
        assertThatThrownBy(() -> RequestFingerprint.scoped(null, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequestFingerprint.scoped(
                new AccessContext(null, "subject", UUID.randomUUID(), AccessPurpose.PROPERTY_MANAGEMENT), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
