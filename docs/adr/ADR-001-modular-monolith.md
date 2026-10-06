# ADR-001 — Modular Monolith First

**Status:** Accepted

OULA starts as a Spring Modulith modular monolith with strict bounded contexts and event-driven collaboration. A module may be extracted only when evidence justifies an independent scaling, security, deployment, technology or team boundary.

This preserves fast learning without sacrificing future service extraction.
