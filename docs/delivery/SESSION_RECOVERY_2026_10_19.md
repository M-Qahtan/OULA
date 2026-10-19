# OULA — Session isolation recovery patch

Date: 19 October 2026 (Asia/Riyadh). Tracking: #42, #38; base PR #120.

**ملخص التنفيذ:** أُصلح عزل البيانات الخاصة عند الخروج وتبديل الحساب أو مساحة
العمل أو الصلاحيات، مع تجاهل الردود المتأخرة وحفظ المسودة عند إعادة فحص الجلسة
نفسها. نجحت 104 اختبارات وحدة و44 اختبار متصفح، وفحص الأنواع والبناء.

**حدود الاستعادة:** النسخة المستعادة هي قاعدة طلب المراجعة #120. مصدر الواجهات
المعتمدة الأحدث والأقسام الثلاثين والفروع التسعين غير متاح حاليًا؛ لا يمثل هذا
الفرع استعادةً لتلك الواجهات. اختبار Java/PostgreSQL المباشر لم يُعَد.

This is a bounded frontend repair on `codex/session-recovery-20261019`, based on
`43c0072575056acedc0ee46693bb46a0fa02ac5b` (`codex/integrated-workspace-delivery`).
It is not a recovery of the later approved cinematic interfaces.

## Recovery status

The source directory used by the preceding local implementation,
`/workspace/scratch/dad637fd4713/OULA`, is no longer available in this session.
The available GitHub refs contain PR #120 but do not contain the later local
`codex/world-session-continuity` or property-care/interface branches. The known
local base `d7dd851554881924d53da916300703784a41f55d` could not be resolved through
GitHub. Previous test-result JSON files remain available; they do not reconstruct
the missing source. The retained browser report records 58 passes and 4 failures,
so it is not evidence that the missing final state was fully accepted.

PR #120 was cloned into an isolated directory and a new branch was created. No
older gateway is presented as the founder-approved later design. The later
30-section / 90-branch interfaces and property-care write workflow remain a
source-recovery gap. Applying this patch to that later implementation requires
review against its actual source, rather than replacing it with this baseline.

## Observable repairs

- Signing out immediately hides private assistant text/plans and remounts private
  forms. The authenticated identity, authorized workspace, membership set,
  purposes, scopes and session mode define that UI boundary.
- Equivalent grants in a different order and a renewed expiry do not erase a
  same-session draft. Initial anonymous session resolution preserves the page
  shell and its keyboard focus.
- Session changes are exclusive through their authoritative verification read.
  An older request cannot restore an obsolete account, even if its transport
  ignores cancellation. Revalidation requested during a change is queued.
- Logout re-reads `/api/session`, preserving the server's local-development
  sign-in configuration. A lost or denied mutation response also triggers a
  server check instead of guessing the cookie's final state.
- Same-origin tabs receive a change signal through BroadcastChannel, with a
  temporary storage signal as fallback. Signals contain no identity, token,
  business record or assistant input. Focus, pageshow, visibility and a
  30-second visible-tab check re-read the current server cookie.
- Account, workspace and permission changes reset private forms and pending
  requests while retaining the workspace URL, language and selected role.

Role selection remains a presentation choice. Authorization continues to be
performed by the existing server bridge and Java services. This repair does not
implement production IdP revocation, refresh-token rotation or centralized
session invalidation.

## Verification boundaries

The unit suite, TypeScript check and production build exercise the current
source. Browser checks use the real Next.js BFF and encrypted HttpOnly cookie
with an explicitly synthetic, loopback-only OIDC/API harness. Its fixed account
profiles are test infrastructure and are not exposed by production routes.

The regression tests first reproduced retained assistant input after sign-out
and an existing tab not noticing another tab's sign-out. A separate deterministic
unit test exposed the mutation lock ending before verification, then passed
after the lock was extended. Final commands and counts are recorded in
`SESSION_RECOVERY_VALIDATION.json`.

No Java/PostgreSQL integration was executed for this patch. The available Java
runtime is Java 17; the project requires Java 21, Maven and PostgreSQL/PostGIS.
The Java source, database migrations, OpenAPI contracts, gateway source and
visual assets are unchanged relative to PR #120. No visual equivalence with the
missing approved interface is claimed.

## Review and integration

Inspect the six frontend/test files plus this delivery evidence. Run the commands
in `SESSION_RECOVERY_VALIDATION.json`. When the later UI source is available,
port the session boundary and hook into its existing navigation implementation,
then rerun both browser checks and Java/PostgreSQL integration before acceptance.

Remote CI, independent maintainer review, founder acceptance, merge and deployment
are separate gates. This patch has not been pushed, merged or deployed. To undo
it locally, revert the focused recovery commit; do not reset or overwrite another
branch containing newer interface work.
