package com.oula.api;

import com.oula.iam.ConsentCode;
import com.oula.iam.ConsentDecision;
import com.oula.iam.ConsentEventView;

import java.time.Instant;

public record ConsentResponse(
        ConsentCode consentCode,
        ConsentDecision decision,
        String reason,
        String source,
        Instant occurredAt
) {
    static ConsentResponse from(ConsentEventView view) {
        return new ConsentResponse(
                view.consentCode(),
                view.decision(),
                view.reason(),
                view.source(),
                view.occurredAt()
        );
    }
}
