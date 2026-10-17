# OULA: source authority / exact evidence-hash attestation, V029

VERIFIED is caller-declared status. Material recommendations and decisions now
require an **additional, separately provisioned**, active, scoped issuer record
and unexpired, unrevoked per-evidence hash attestation. The guard checks subject,
workspace, evidence type, source identity/type, jurisdiction, exact hash and
wall-clock validity after acquiring shared row locks.

No public attestation/provisioning API exists. Database TEST_ONLY authority rows
in JUnit cannot establish real independent verification. Legacy unattested
material evidence now fails closed; before any production rollout, inventory and
independently verify historical sources, prepare a backed-up migration and
evaluate impact. Documentary observation policy remains distinct.

Future phases: signed source credentials, regulated source onboarding, property-
specific legal claim proof, issuer rotation and revocation, replayable decision
input snapshots, measured calibration and field acceptance. ROS untouched.
