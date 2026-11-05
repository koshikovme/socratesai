# English, Russian and Kazakh interface

Date: **2026-11-05**. Local implementation and checks are complete; production promotion
and live three-role verification are pending in this initial source report.

- A shared plain-text translation layer covers English (`en`), Russian (`ru`) and Kazakh
  (`kk`), with named interpolation and no new runtime dependency or raw HTML rendering.
- The language selector is available in the header, login, registration and session-recovery
  page. It shows English / Русский / Қазақша, updates immediately and sets document language.
- The nonsecret preference is stored under `socrates-ui-locale`, survives reload/logout and
  takes priority over the initial browser language. Blocked storage uses in-memory selection.
- Catalog, collections, workspace, program-format help, mentor controls/metadata, verdicts,
  progress/profile, staff drafts/tests/verification, collection ordering and admin role forms
  are translated, including accessible labels, confirmations, validation and safe error text.
- API role/difficulty/verdict values, source buffers, authored task content and generated AI
  advice retain their original data. Locale changes do not request AI, submit code, reset the
  editor or discard an unsaved draft. Profile dates follow the selected locale.
- Mobile navigation wraps to fit longer labels. Unknown future enum/message values use a
  safe text fallback; backend error bodies still pass through the existing safe error map.

Validation: **107 frontend tests**, coverage thresholds, ESLint and production build passed.
Three Chromium protocol-fixture journeys passed, including all three languages, login/reload/
logout persistence, mobile width 390, original Monaco buffer/revision preservation and a
localized sample-result display. Unit checks cover translated staff payloads, hidden-output
masking, error redaction, interpolation/escaped content and complete translation parameters.
These are synthetic UI checks, not classroom or model-quality evidence. Backend schema,
models, experiments, papers and dedicated execution runtime are unchanged.

`scripts/delivery/live_localization.cjs` provides the authorized HTTPS three-role acceptance
without staff writes or execution requests. The production outcome and immutable release
manifest will be recorded after automatic delivery succeeds.
