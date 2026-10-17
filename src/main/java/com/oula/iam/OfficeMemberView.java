package com.oula.iam;

import java.util.UUID;

/** Administrator-only directory view; does not confer authority. */
public record OfficeMemberView(UUID actorId, String subject, String role, String status) {}
