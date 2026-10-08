package com.oula.integration;

public interface IntegrationIngressPort {
    InboundIntegrationReceipt accept(
            VerifiedExternalPrincipal principal,
            InboundIntegrationCommand command
    );
}
