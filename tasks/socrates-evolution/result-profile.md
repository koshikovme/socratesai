# Editable accounts and clear teaching/student profiles

Date: **2026-11-05**. Implementation and local verification complete; production release and
synthetic-account live acceptance are pending. No real student account has been edited.

## Account behavior

Profile now has an **Edit profile** form for name, sign-in email, university and study group,
with cancel, save status and safe errors in English/Russian/Kazakh. Blank optional study fields
are cleared. Role remains administrator-managed; existing theme preference is retained.
All values render as escaped text. Unsaved form values survive interface-language changes.

`GET /api/users/me` and the existing progress response add an opaque `profileRevision` and
account creation date. `PUT /api/users/me/profile` accepts the four editable fields plus that
revision. It requires `currentPassword` only for email changes, rejects unrecognized fields
(including role, ID and internal-account flags), validates lengths/email and checks email
uniqueness. Errors use the standard safe envelope; no password/token/database details escape.

Profile/settings writes lock and refresh the same user row. Concurrent/stale profile updates
return 409 rather than overwriting another tab. A concurrent theme save cannot overwrite
profile fields. No migration or change to `interaction_logs` / `PolicyFeatures` was needed.

Email changes return replacement authentication for the same account ID. The client applies
it without logging out/resetting other Pinia stores, preserving private editor buffers.
JWTs bind both the sign-in email and immutable user ID, preventing an old email token from
accessing a later account that registers the released address. Login issues tokens for the
verified principal ID even if the email changes during authentication. Existing tokens without
the new identity claim require one new sign-in after deployment. Passwords are not changed.

## Role-appropriate information

The screenshot's block was a mentor-event history for research/diagnostics, not submission
history or grades. Removed its raw “Legacy activity”, execution-unknown/action labels,
policy charts and latency counters from the normal profile. Underlying interaction data and
expert/dataset exports are preserved.

Students see solved/attempted tasks and formal submission counts with an explanation that
only ACCEPTED graded submissions solve a task. Sample runs and hints do not count as attempts.
Teachers/admins see guidance and a direct link to Teaching → task/collection → Student results;
admins additionally get the role-management link. There is no manual grading workflow.

## Genuine student reporting

Staff result lists, aggregate counts, filters and direct source-review access now consistently
admit only current STUDENT accounts and exclude configured internal fixture IDs. Both teachers
and administrators use this student-reporting scope. Excluded detail URLs return safe 404.
Personal owner history remains accessible and is never deleted.

`APP_REPORTING_INTERNAL_USER_IDS` is a bounded comma-separated list of immutable positive IDs,
configured externally by the operator. Display-name/email edits cannot evade it. Production
classification was prepared from the authenticated identities of the 32 known synthetic
student/acceptance/load accounts. New operational accounts must be added to this list before
running future checks. Never classify a real student by matching their name or email text.

## Validation

- Local Java verify passed **335 tests** (zero failures/errors, one opt-in research skip), including
  isolated PostgreSQL/Flyway account-edit tests and staff-result authorization. Login identity-race and concurrent role/profile
  write regression tests also passed.
- Frontend: **144 tests**, coverage gates, lint and production build passed.
- **Five Chromium fixture journeys** passed, including mobile teacher/student profile editing,
  three-language switching, email credential rotation and reload at 390 px.
- Nine delivery/import/parser tests passed. Sandbox programs run only on the dedicated executor;
  profile checks do not execute programs or change reference suites.

These are implementation/security and synthetic acceptance checks, not evidence of classroom
learning gains, student preference or a new trained ML model.
