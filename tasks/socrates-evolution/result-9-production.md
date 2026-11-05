# Step 9 — authorized production deployment

Verification date: **2026-11-05**, Asia/Almaty. Live application:
**https://socratesai.duckdns.org**. The repository owner explicitly authorized deployment,
use of the two purchased hosts and private credentials, synthetic live tests, commits and pushes.
No additional paid resources were purchased. All test accounts/events in this report are
synthetic acceptance fixtures, not real student research data or evidence of learning gains.
The earlier local-only report is preserved in [result-9-local-demo.md](result-9-local-demo.md).

**Current release:** v0.9.4 (`8f7f917`) is deployed through successful workflow 41965872170.
English/Russian/Kazakh interface and real three-role mobile checks are recorded in
[result-ui-localization.md](result-ui-localization.md), with the
[current immutable manifest](../../deploy/releases/v0.9.4.json).
The preceding v0.9.3 (`c53bf74`) native AI/workspace follow-up remains dated below.
Code-only Gemini/ML guidance, runnable starter/reset UX, compact header, thirty real concurrent
AI hints and the APT 2.6.2 image fix are verified in [result-mentor-v2.md](result-mentor-v2.md),
with its [immutable manifest](../../deploy/releases/v0.9.3.json).
The v0.9.1 measurements/recovery rehearsals below remain historical evidence; in particular,
native Java/C++ and model-outage guidance now uses Gemini instead of the earlier RULE mode.

**Release status: completed.** Release **v0.9.1**, source
`95d2904477eb6b2a7fa519eb7c3e0b91691231c4`, passed the complete
[release workflow 41922786265](https://github.com/koshikovme/socratesai/actions/runs/41922786265),
including registry publication and automatic restricted-SSH production promotion. Fresh
three-language Run/Submit/idempotency, the 12-case verdict matrix, real HTTPS/WSS browser,
hidden-echo/ownership/spoofing and negative WSS checks passed **after** promotion. Readiness,
TLS and unchanged schema V13 passed. The nonsecret permanent release manifest is
[deploy/releases/v0.9.1.json](../../deploy/releases/v0.9.1.json).

The host's actual immutable application references are:

| Service | GHCR digest (repository prefix `ghcr.io/koshikovme/socratesai/`) |
| --- | --- |
| backend | `backend@sha256:ad8c202d689c430ebb03f063e237e5717a917694ec113ebc5e101c11f9201f20` |
| frontend | `frontend@sha256:0ea5540e5a385689b8b8d8abdc947823cfba4b730d7c881847c8f680a9deead5` |
| policy-api | `policy-api@sha256:9934437454124ff31eb0c93878bd3429a75249fd4e31ece1f8bde2947fecdec9` |

The final operational/documentation commit follows the tagged application commit; it does
not change the deployed application code or silently move the release tag.

## Installation and configuration

| Component | Observed deployment |
| --- | --- |
| Application VM | Hoster.kz `89.126.199.55`, private `172.16.0.2`, 2 vCPU / 4 GiB / 50 GB, Ubuntu 26.04, cgroup v2 |
| Execution VM | Hoster.kz `89.126.199.58`, private `172.16.0.3`, 1 vCPU / 4 GiB / 50 GB, Ubuntu 22.04, kernel `5.15.0-202-generic`, cgroup v1 |
| Container tools | Docker 29.9.3 and Compose 5.7.0 on both VMs |
| Application edge | Public HTTP 80 → HTTPS 443, real Let's Encrypt certificate, SPA history routing and same-origin API/SockJS/STOMP |
| Private execution transport | WireGuard `10.93.0.1` → `10.93.0.2:2358`, independent API auth, provider-private-network underlay |
| Private application services | Spring Boot, FastAPI and PostgreSQL have no public host ports; separate Docker frontend/backend networks |
| Persistent data | Named application PostgreSQL/model-cache/operations-log volumes; independent executor PostgreSQL/Redis AOF volumes |
| Database role | `socrates` for backend; `judge_runtime` for executor, both NOSUPERUSER/NOCREATEDB/NOCREATEROLE |
| Schema | **13** successful Flyway migrations: V1–V12 plus V13 durable Gemini quota counters |
| Operations owner | Repository owner; operator SSH identity and recovery material held outside Git |

Only the explicitly authorized hosts were modified. No connections or tests used shared
DBs 21/22. Password SSH was disabled after installing verified operator/deploy public keys.
Fail2ban, firewall, log rotation, host file limits and 1-GiB swap were configured. Main ingress
permits SSH/80/443 and peer-restricted WireGuard; unsolicited/routed traffic from `wg0` is denied.
The executor's public DNS name is not a public execution endpoint.

Application ceilings are backend 1536 MiB/PID 256, policy 768 MiB/PID 96, PostgreSQL
1024 MiB/PID 128 and edge 128 MiB/PID 64, with explicit CPU limits. These are limits sharing
one VM, not memory/CPU reservations. The executor has one worker, 2-GiB/PID-256 worker ceiling,
and separately limited API/database/Redis containers. Only its Isolate worker is privileged.
API/model/edge/backend/database/Redis use explicit unprivileged users and appropriate
read-only filesystems, dropped capabilities and no-new-privileges settings.

Secrets were generated using the OS cryptographic random generator and imported only into
protected external runtime files/environment. No physical RNG/HSM certification is claimed.
The owner-supplied Gemini key is stored outside Git/build contexts. Main configuration is
`/opt/socratesai/private/production.env` (0600). Executor configuration is separate and has
no application DB/JWT/Gemini credentials. Bootstrap admin settings were removed after creation.
Private owner access is in `.delivery/production-access.md`; no password or JWT is in this report.
The owner confirmed DuckDNS token rotation; this is owner attestation, not an API revocation audit.

## Executor security correction and actual runtimes

The complete scan of the initial official Judge0 1.13.1 image found Debian 10 and **3,632
fixed HIGH/CRITICAL findings** (385 critical, 3,247 high). New execution was disabled,
the legacy engine stopped, and that image was rejected. It is not an approved rollback target.
Legacy acceptance evidence was kept separately and is not used to certify the replacement.

The replacement packages upstream Judge0 commit `d0632cffaaf4fcdbf83a83e7a55ed9baf8a86db6`
and Isolate `ad39cc4d0fbb577fb545910095c9da5ef8fc9a1a` with mandatory archive checksums.
It uses Debian 12.15, Ruby 3.4.12, Rails 8.1.4.1 and a checksum-bearing frozen Bundler lockfile.
`modernize.rb` supplies bounded compatibility/configuration/log filtering patches; execution
still uses the standard Judge0/Isolate implementation, not a project-written process runner.
Source and changes are packaged in the image. This is project-maintained packaging, not an
upstream promise of support or a proof against unknown escapes.

Certified execution image: local immutable reference
`socratesai/judge0@sha256:3e554e1a3764edd2fa1acceac63854a3dcb3ea1b2359d5c4b77a5b599c1cb075`.
The local reference identifies the operator-built image, not a claim that Docker Hub serves it.
An exact compressed image archive was copied off-host and checksum-verified; its SHA-256 is
`c561de10a87b2595e862c697af6a55fa54d3a51d7960b2817831df32c037c738`.
Loading that archive was verified locally without executing student source; the recovery
image ID is `sha256:3e554e1a3764edd2fa1acceac63854a3dcb3ea1b2359d5c4b77a5b599c1cb075`.
CI publishes a separately built execution artifact; changing the live executor digest requires
fresh on-host certification and explicit maintenance, not automatic application promotion.

| Language | Real catalog ID / full name | Entry point |
| --- | --- | --- |
| Python | 1001 / `Python (3.12.16)` | `main.py`; isolated py_compile before execution |
| Java | 1002 / `Java (OpenJDK 21.0.13)` | `Main.java`; bounded compiler/runtime heaps |
| C++17 | 1003 / `C++ (GCC 12.2.0)` | `main.cpp`; GCC C++17 compile |

These IDs were discovered from this server, not borrowed from the public demo. The external
catalog is committed as `deploy/sandbox/production-runtime-catalog.yaml`. Published problem 1
was upgraded through a new immutable **version 2** and verified in all three languages before
publication; collection 1 now pins that version. Version 1 and historical submissions were
preserved. Both adapter and worker were re-enabled after the replacement's security checks.

Trivy 0.76.1 reported **0 fixed HIGH/CRITICAL** for the final replacement engine and Redis.
The three application images also passed that gate locally and in main CI. Stock PostgreSQL
findings in the unused `gosu` Go helper remain separately reported (26 in the recorded scan).
Both PG containers run directly as UID 70 with read-only rootfs/capability restrictions, so
that helper is not invoked. This is an explicit bounded exposure review, not an all-stack
zero claim. Lower-severity/unfixed/unknown issues and future advisory changes remain outside
this gate. Rejected legacy images and dedicated-host build caches were removed; DB volumes
were retained.

## Live verification evidence

The following checks use the replacement engine unless specifically labeled historical.
Every student/reference program was sent to the isolated execution host; none ran on the
operator machine, application host, FastAPI or GitHub Actions runner.

| Check | Observed result |
| --- | --- |
| Teacher runtime upgrade | New draft, all three reference solutions ACCEPTED across sample/hidden tests, atomic publication and collection repin |
| Student positive API journeys | Python/Java/C++ real Run and formal Submit ACCEPTED; repeat idempotency key returns same submission; profile SOLVED |
| Per-language verdict matrix | 12/12: valid AC, wrong output WA, invalid syntax CE, runtime failure RE for each language |
| Provider resource/isolation probes | 9/9: three compile errors; infinite-loop TLE; output SIGXFSZ; filesystem/environment/network denial; bounded forks; memory pressure; bounded compiler pressure |
| Application verdict/resource probes | 8/8: WA, CE, RE, TLE, output limit and filesystem/environment/network/process/memory outcomes mapped through the native API |
| Memory distinction | Allocation pressure returned provider status 12 / RUNTIME_ERROR without over-cap measurement; no fabricated MLE from stderr |
| Live browser | Actual HTTPS, Monaco, separate Python/Java buffers, real sample run, graded submit, 2/2 tests, mentor messages and logout/access guard |
| WebSocket browser evidence | 1 CONNECTED, 3 MESSAGE, 0 STOMP ERROR, 0 unexpected page errors; two Monaco cancellation events during rapid model switches recorded separately |
| Negative WSS authorization | Anonymous CONNECT and foreign subscription rejected on the real WSS endpoint |
| Roles/data boundaries | Public staff signup denied; foreign teacher/unauthorized student resource access denied; body studentId cannot change authoritative identity |
| Hidden projections | No hidden input/expected output/reference solution in student catalog or attempt DTOs; source/stdout/stderr/compile output absent from submission case projections |
| ML/LLM | Real Gemini-phrased Python mentor response observed; Java/C++ use RULE/UNSUPPORTED_LANGUAGE; real policy outage uses RULE/ML_UNAVAILABLE/template |
| Research export | Legacy policy dataset header/fields preserved with additive native evidence_origin metadata; compatibility regression tests pass |
| TLS | Valid hostname/certificate, HTTP 308, TLS 1.3, certificate expiry 2027-02-02, ACME renewal dry-run passed; actual WSS browser traffic accepted |

The memory/compiler pressure tests are bounded synthetic probes, not uncontrolled fork bombs.
The provider's actual per-probe durations ranged approximately 1–8.4 seconds including polling;
compile pressure finished in 8.34 seconds with CE. Final logs and exact status-only evidence
remain in ignored operator artifacts. There were zero remaining Isolate box directories
after the probes. Both absent and invalid execution authentication returned HTTP 401.
The inspected 30-minute Docker log sample on both hosts had zero known-source/secret/JWT
matches; that is a bounded audit, not a claim about all possible future logs. External
checks confirmed DB/ML/backend/execution API ports closed; only main edge 80/443 was reachable
among the checked application ports. No test bodies, provider tickets or private rows are
committed as evidence.

The 30-session rehearsal held **30 authenticated WSS connections**, received 30 Java RULE
mentor replies and completed **30 real Python sample runs**. POST 202 p95 was **1,931.8 ms**;
maximum sample completion was **32.2 s**. Runs created **zero graded attempts** and no false
solved progress for those fixtures. This is one short public sample, one executor worker and
synthetic clients; it does not establish an arbitrary-problem classroom SLA or long-duration
capacity. The latest raw summary is `.delivery/production-capacity.json`; historical legacy
measurements have `-legacy` suffixes. No research benchmark output was overwritten.

## Recovery, storage and budgets

- Backend KILL/start during RUNNING after two persisted provider tickets: generation **1 → 2**,
  worker attempt **1 → 2**, final ACCEPTED. Exactly two provider executions, unchanged student
  attempt number, no duplicate submission or blind remote POST replay.
- Real executor API outage: terminal SYSTEM_ERROR after bounded handling, no false solved
  progress. API and worker recovered. Real model outage: RULE/template fallback, then readiness
  recovered after loading both saved models.
- Compatible-image rollback was actually rehearsed with source `66e4175` rebuilt on patched
  bases, Gemini disabled, and unchanged additive V13 database. Catalog/readiness passed,
  current images were restored and data/schema retained. This does not test arbitrary schema
  downgrades or claim the rejected old executor is safe.
- Daily 02:30 UTC encrypted custom pg_dump, AES-256-GCM CMS, SHA-256 verification, 14-day host
  retention. The main host holds the recipient certificate only; private recovery key stays
  on the operator workstation. No plaintext dump was retained on the server.
- An actual encrypted snapshot was restored into a disposable network-isolated PostgreSQL
  container under a nonsuperuser role: the final restore rehearsal contained **13 migrations,
  35 synthetic users, 16 submissions and 3 progress rows** at backup `socrates-20261104T221303Z`.
  The temporary plaintext dump and restore container were removed. A subsequent final backup
  `socrates-20261104T222203Z` includes the post-release acceptance writes and was copied off-host.
- Workstation daily 03:00 UTC encrypted off-host copy timer is installed and has succeeded;
  five encrypted backup files were checksum-verified after the final release.
  It catches up when the workstation is online/logged in; it is not an always-on backup store.
  Recovery-key safekeeping and local backup retention remain operator responsibilities.
- Safe health/disk checks every minute, Docker logs 10 MiB × 3, journal 150 MiB/7 days,
  operations named-volume logrotate. External email/SMS alert routing is not configured.
- Sample data expires through bounded application cleanup; provider submissions/source/stdin
  older than 24 hours are removed by the dedicated host's hourly retention job. Formal graded
  history is preserved pending the owner's long-term retention/deletion policy (P5.10).

Gemini uses `gemini-3.5-flash-lite` and the existing funded project. Durable shared quotas
allow at most 3,000 monthly global requests, 30/minute and 10/student/day. Actual countTokens
checks cap input at 2,048 and generated output at 128 tokens. At the
[reviewed model prices](https://aistudio.google.com/docs/pricing#gemini-3.5-flash-lite),
the configured conservative total is **$2.8032/month**. Quota/unavailable/invalid responses
fall back to templates. This estimate excludes tax, price changes and calls using the same
key outside SocratesAI; it is not a billing-account hard cap. NO_FEEDBACK makes no LLM call.

Saved model hashes are unchanged:

- Action model: `f38cb4cb0476f7544eaa006663c297e44b5025caec9305de9685ce8c2df3ddfb`.
- Python mentor-state model: `e7be65df21e20cdd005d54139cc17c1a37ee348751b17713cd7f712e00dcf13d`.

## Code, CI and reproducibility

Implementation is in commits `270f8ad`, `abe4d33`, `79b1a7e` and `95d2904`, followed by this
operational handoff. Relevant added paths are `deploy/host/`, `deploy/sandbox/`,
`scripts/delivery/`, V13 and the Gemini quota/service tests. Main CI
[41921673860](https://github.com/koshikovme/socratesai/actions/runs/41921673860) passed all jobs,
including the corrected restricted SSH health check and four-image build/security gates.
Earlier `v0.9.0` published registry images but its deploy failed SSH_KEY_INVALID; it is not
claimed as a successful automatic release. The owner replaced the secret and the new
connection gate passed. Environments are created by jobs; repository secrets/variables are
configured. No permanent registry token or general operator SSH key is granted to Actions.

Local backend verification: **305 tests, 0 failures/errors, 1 opt-in experiment skipped**,
both coverage gates passed. Frontend: **84 tests**, lint and production build passed. CI
also repeats backend/frontend, Python contracts/model readiness, two browser protocol fixtures,
Testcontainers/Flyway, actionlint/shell checks and four real build-context exclusion checks.
The Maven cold-build issue was fixed by installing `unzip`; the pinned ZIP checksum was kept.

Reproduction and incident commands are in [DEPLOYMENT.md](../../DEPLOYMENT.md).
Post-release commands from the operator checkout (private input files stay ignored):

```bash
python3 scripts/delivery/live_acceptance.py --access-file .delivery/production-access.json --run-label v0.9.1
python3 scripts/delivery/live_runtime_matrix.py --access-file .delivery/production-access.json
node scripts/delivery/live_browser.cjs --access-file .delivery/production-access.json
```

These ran successfully with Node 22.23.1/Chrome and the real public HTTPS endpoint.
`live_capacity.py` and `live_recovery.py` have also passed their documented real-host rehearsals.
The new operational helpers pass syntax checks; promotion tests (4), demo fixture tests (5),
four context checks and pinned actionlint passed. Application code was not changed after
its successful release gates.
Live acceptance/recovery/capacity helpers are restricted to the two authorized hosts and
require ignored private access/key files. Startup CI executes only `/bin/true` with fixture
credentials; privileged/student execution is never enabled on PR runners.

## Remaining operational decisions

Step 9 is closed for the recorded production release and synthetic acceptance scope. Before depending on unattended
classroom operation, the owner should select an external alert destination, protect/copy the
recovery key independently, decide graded-source retention/deletion and prepare the actual
teaching problem bank/accounts. Keep the one-worker starting limits and watch queue pressure
for compiler-heavy tasks. These are explicit handoff limits, not missing compilers or a need
to buy a managed Judge0 subscription/Vercel/domain. No new ML training or paper metric changes
were made; public Codeforces/model/synthetic policy/real interaction evidence remain separate.
