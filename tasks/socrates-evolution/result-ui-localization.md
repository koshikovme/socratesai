# English, Russian and Kazakh interface

Date: **2026-11-05**. **Deployed and verified** at **https://socratesai.duckdns.org**.
Release **v0.9.4**, source `8f7f9174b846299b898a381911bc02a78449b284`, passed
[automatic release and deployment](https://github.com/koshikovme/socratesai/actions/runs/41965872170).
Exact digests, unchanged model/executor identifiers and schema V13 are recorded in
[the immutable manifest](../../deploy/releases/v0.9.4.json).

- A shared plain-text translation layer covers English (`en`), Russian (`ru`) and Kazakh
  (`kk`), with **396 messages**, named interpolation and no new runtime dependency or raw HTML rendering.
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

`scripts/delivery/live_localization.cjs --access-file .delivery/production-access.json` passed
against actual HTTPS after promotion, using the authorized synthetic admin, teacher and
student identities. Russian/Kazakh admin forms, teacher editing, English/Russian/Kazakh Monaco
controls, reload/logout persistence and original unsaved form/code preservation passed at
mobile width **390 px**. **Zero staff writes, execution POSTs or unexpected page errors** were
observed. Screenshots/evidence and credentials remain protected and ignored under `.delivery/`.
The health/readiness/disk check passed. No paid translation API or extra hosting was added.

Earlier native AI/judging/capacity evidence remains dated in [result-mentor-v2.md](result-mentor-v2.md);
it was not relabeled as a new model or classroom evaluation. This release changes interface
language, with generated advice and teacher-authored task content retaining their original text.
