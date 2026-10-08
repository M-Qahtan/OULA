package com.oula.integration;

public record CreateIntegrationPartnerCommand(
        String partnerCode,
        String partnerType,
        String displayName,
        String jurisdiction,
        String authMode,
        String credentialReference,
        boolean inboundEnabled,
        boolean outboundEnabled
) {}
