# Step 8 — Reproducible application packaging and CI/CD

Implemented and locally verified on 2026-10-22, starting from `94a021c`. This delivers the requested application images, HTTPS edge, private Compose topology, production configuration validation and release-tag publication workflow. It does not claim a hosted deployment, a completed remote Actions run, an issued public certificate or an installed Judge0 server.

## Delivered package

| Component | Build/runtime and verification |
| --- | --- |
| Backend | Java 21 JDK build with Maven dependency cache; Boot tools extraction into separate dependency/loader/application layers; Java 21 JRE runtime, UID/GID 10001; no `javac` or `jdk.compiler` module |
| Frontend | Node 22.23.1 locked build; Nginx 1.30.6 runtime, UID/GID 101, external TLS, SPA history fallback, same-origin API and SockJS/WebSocket proxy |
| Policy API | Python 3.12.15, hash-locked wheel-only serving subset, UID/GID 10001, exactly two checksum-verified saved models, atomic lifespan initialization and readiness |
| Database | Digest-pinned official `postgres:16-alpine`, explicit UID/GID 70, persistent named volume and new-volume application-role initialization |

Dockerfile frontend and every base image are pinned by immutable digest. Maven wrapper 3.9.14 ZIP is SHA-256 pinned after verification against its published SHA-512. JAR archive timestamps are fixed. Frontend uses the committed npm lock. Serving/test Python dependencies have full generated hash locks constrained by the canonical `ml/requirements.txt`; training-only libraries are not installed in the serving image. BuildKit caches are optional accelerators, not runtime dependencies. Only linux/amd64 was built/verified here; bit-identical multi-architecture builds are not claimed.

Final local BuildKit image references (local image identity, not an already-published registry release):

| Local tag | Image identity | Docker size |
| --- | --- | --- |
| `socratesai/backend:delivery-test` | `sha256:40f7c98ab67cd544e9b0414ebf02e22cbfe75745fafd8533d5152cacc4c0f5ea` | 162,424,614 bytes |
| `socratesai/frontend:delivery-test` | `sha256:d77d2823a8a2ec9b292464c45cc99988b50aa867d7340aa5761a630aa68c9c06` | 26,800,948 bytes |
| `socratesai/policy-api:delivery-test` | `sha256:6530dd3042c66eaf8a6547860259307c9c4f9ef4b83d7832411a0d5125e1e051` | 235,798,996 bytes |

Final repeated builds succeeded with cached stages. Published pull digests will be recorded by the actual release workflow; local identities above are not represented as GHCR publication evidence.

## Networks, TLS and startup

`docker-compose.prod.yml` has four services. Only `frontend-nginx` publishes 80/443 by default. The edge/backend share `frontend-net`; only backend/PostgreSQL/policy-api join internal `backend-net`. There are no backend, database or ML host-port mappings. Backend egress remains available for explicitly enabled external providers/Judge0. Application containers receive no Docker socket, host source, datasets or compiler mounts.

All services use read-only root filesystems, bounded tmpfs/pids/memory/CPU, dropped capabilities, `no-new-privileges`, rotating logs and graceful shutdown. PostgreSQL runs directly as UID 70; the new-volume hook creates the application login with NOSUPERUSER/NOCREATEDB/NOCREATEROLE, database/schema ownership sufficient for Flyway, and separate operator credentials. Password assignment suppresses SQL statement/error-statement logging. Existing volumes are never silently reinitialized or their passwords changed.

The edge uses mounted `fullchain.pem`/`privkey.pem`, HTTPS redirect to the exact public origin, TLS 1.2/1.3, security headers, revalidated HTML, immutable assets and same-origin workers. API paths are preserved; API/WS proxy caching is off, SockJS buffering is disabled, upgrade headers and 90-second WS timeouts are explicit. Docker DNS is resolved dynamically after backend replacement. Client forwarding chains are overwritten. Access logs contain request ID/method/status/size/time, not bodies, Authorization or request URIs.

`.env.example` contains empty mandatory password/signing/TLS inputs, explicit limits, switches and nonsecret deployment placeholders. No live secret or generated test key is committed. First-admin bootstrap is external and opt-in. LLM providers default off. Judge0/judging default off because no separate host has been certified; there is no fake language ID, in-process compilation or public-demo fallback.

`ProductionConfigurationValidator` is registered as an application-context initializer and runs before beans/Flyway/connections. It validates signing material, credential/origin/endpoint rules, policy thresholds, provider prerequisites and incompatible judging/logging settings without echoing values or causes. Existing configuration classes retain their duration/quota/runtime validation. A real container negative-start test confirms invalid signing configuration fails before DB initialization and does not print the sentinel value.

A discovered production defect was fixed: CORS and STOMP had ignored the configured origin and allowed only localhost patterns. Production now uses only the exact configured HTTPS origin; development behavior stays compatible. Actual allowed/foreign Origin and authenticated STOMP were tested through TLS Nginx.

## ML readiness and artifact continuity

- `policy_api.py` uses FastAPI lifespan startup; loaded objects are published together only after both joblib loads and callable checks succeed. Failed loads leave both unavailable. `/ready` and compatible `/health` return 503 until ready; `/live` provides liveness. Error text is fixed and does not reveal artifact paths or source.
- `ml/models.manifest.json` verifies SHA-256 before deserialization. The image includes only the default action model and native Python source model. It loads them without network or training.
- Source model SHA-256: `e7be65df21e20cdd005d54139cc17c1a37ee348751b17713cd7f712e00dcf13d`.
- Action model SHA-256: `f38cb4cb0476f7544eaa006663c297e44b5025caec9305de9685ce8c2df3ddfb`.
- The packaged action model follows `policy_api.py`'s `mentor_policy_model.joblib` default; the previous production Compose selected `problem_suite_policy_model.joblib`. Alternative action-study artifacts remain available for explicit research packages. Native `/predict-mentor-state` still uses the unchanged source model.
- `PolicyFeatures`, mentor language gating, confidence threshold, prediction endpoints, old export/log fields, migrations V1–V12 and saved model/evaluation results remain unchanged. No models were trained, experiments rerun or paper metrics updated. Inspected the IEEE integration/software statements; this serving/readiness/security packaging does not change their endpoint/model-library claims. No TeX/PDF/duplicate paper edits were needed.

## Security patches and scan policy

Initial real Trivy scans found fixed HIGH/CRITICAL issues in the old dependency/base selections. Fixed rather than suppressing them:

- Spring Boot 4.0.5 → 4.0.8; explicit compatible patch overrides Jackson 2.21.8/3.1.8 and Tomcat 11.0.27. Removed an unused Spring AI milestone BOM and aligned the Flyway starter with the Boot parent. All backend contracts and PostgreSQL/WS tests were rerun.
- FastAPI 0.116.1 → 0.159.1, permitting patched Starlette 1.9.0; preserved the direct scikit-learn/pandas/joblib pins and saved artifacts. Updated Python base to a patched digest (3.12.15).
- Updated the Nginx base to a patched digest (1.30.6). Node remains 22.23.1. Added ESLint 10 and the essential Vue rules, including a hard prohibition of `v-html`.

Trivy 0.76.1 is digest-pinned. Final scans of the **three built application images** report **0 fixed HIGH/CRITICAL vulnerabilities**, and their CycloneDX SBOMs are generated. The workflow blocks publication on these findings or scanner failure. This is a defined gate, not a claim of zero lower-severity/unfixed issues forever.

**Stock database image finding:** the separately scanned official PostgreSQL image reports **26 fixed HIGH/CRITICAL Go-stdlib findings**, all in `/usr/local/bin/gosu`. Its entrypoint invokes that helper only when UID is 0; this Compose starts UID 70 and drops capabilities. The image/report is not altered, findings are not hidden or given a blanket CVE exemption, and the upstream report is uploaded separately without the application-image zero-finding claim. Review/update that base or approve a verified hardening/VEX decision before a real production release. This remains an explicit operational security follow-up.

## CI/CD behavior

`.github/workflows/production-delivery.yml` replaces the duplicate mutable-action test workflow:

1. Main/develop pushes, PRs and manual runs: Java 21 wrapper verify/coverage/Testcontainers/Flyway; Node locked install/ESLint/coverage/build/Chromium journeys; Python hash-locked inference/readiness/syntax checks; actionlint, shell syntax and actual build-context exclusion tests.
2. After all checks: build the three digest-based images, start a new-volume production Compose fixture with generated credentials/TLS, run security gates and upload JSON/SBOM evidence.
3. Version-tag pushes only: save the tested images as an artifact; require a valid version tag in main history; use the `release` environment and a publish-only `packages:write` token; load/promote the same image artifact to GHCR with version and commit tags. Do not overwrite an existing version tag or automatically publish `latest`.
4. Record image pull digests, source commit, model checksums and platform/base references. Test reports/security results retain 7 days, verified image artifacts 3 days and release manifests 30 days.

Every action is commit-SHA pinned, default permissions are read-only, checkout credentials are not persisted, and PR jobs receive no production/sandbox credentials. Release environment reviewers/tag restrictions must be configured in repository settings; YAML does not establish them automatically. No SSH host/deploy secret was invented. Staging/production rollout, backup locks and a compatible-image rollback are documented in `DEPLOYMENT.md` and await host-specific implementation/verification.

## Local verification

| Check | Actual result |
| --- | --- |
| Java 21 `bash mvnw -B verify` on final dependencies | **296 tests, 0 failures/errors, 1 existing opt-in skip**, both JaCoCo gates passed |
| Production config tests | 14 new cases including early initializer registration, secret redaction, origins/logging/providers and development compatibility |
| Frontend `npm run lint`, `npm test`, `npm run test:coverage`, `npm run build` | Passed; **83 tests**; coverage 72.58% lines / 68.75% statements / 62.11% functions / 65.60% branches, thresholds unchanged |
| Frontend `npm run test:e2e` | **2 production-preview protocol-fixture scenarios passed**, 9.6 seconds |
| New Python hash-locked test environment | **7 inference/language/privacy/saved-artifact + 4 readiness/atomicity/checksum tests passed**, legacy action-model smoke passed |
| Three final `docker build` commands | Passed; base images resolved by digest; cache stages reusable |
| `scripts/delivery/smoke.sh` | Passed on a new private project/volume; invalid config rejects before DB; HTTPS redirect/deep SPA/script/API/auth/roles/origins/STOMP; all 12 Flyway migrations; non-superuser application role; no compiler/private host ports |
| Network-free packaged model load | Passed; Python 3.12.15 / FastAPI 0.159.1 / Starlette 1.9.0; manifest checks succeeded |
| `scripts/delivery/context_check.py` | All 3 real Docker ignore policies passed synthetic `.env`, nested secrets/keys, dataset, experiment, TeX and node_modules exclusions |
| Digest-pinned actionlint and shell syntax | Passed |
| `scripts/delivery/scan.sh` | Passed the 3 application gates, generated JSON/SBOMs and separate upstream PostgreSQL report as described above |
| `git diff --check` | Passed |

Local logs: `/tmp/socrates-step8-verify-final.log`, `*-frontend-tests.log`, `*-frontend-coverage.log`, `*-lint-final.log`, `*-e2e.log`, `*-python-final.log`, `*-{backend,frontend,policy}-build-final.log`, `*-smoke-final.log`, `*-context-final.log`, `*-workflow-lint-final.log`; JSON/SBOMs in `/tmp/socrates-step8-scans-final/`. Prefix for `*` is `/tmp/socrates-step8`. Temporary test credentials/certificates/volumes are deleted by the smoke script. No shared DB, real student execution or paid provider calls were used.

Non-blocking observations: the existing lazy Monaco chunk size warning remains; saved joblib/NumPy and Starlette HTTPX test-client deprecations are visible. These are not hidden or presented as new research results.

## Remaining external evidence

- P8.5/P8.8: actual engine-specific isolated sandbox manifests/image/compiler pins and trusted live execution CI require the absent Judge0 host; `deploy/sandbox/README.md` records the prerequisites.
- P8.10: GHCR publication is configured, but no release version/tag was supplied or created. No remote registry publication or remote CI success is claimed.
- P8.11/Step 9: host identities, staging/production deployment workflows/locks, public TLS renewal, backup/restore and real browser/judging/rollback verification remain pending hosting inputs. Current local smoke proves a new-volume container start on this Docker host, not a newly provisioned physical host or reachable production deployment.
- Stock PostgreSQL `gosu` findings, institutional long-term graded-source retention (P5.10), multi-instance admission/routing and generic staff-reference recovery remain documented follow-ups.

User authorization covers the scoped commit and Git push. Unrelated untracked datasets, papers, AGENTS.md and local research files are excluded.

Implementation references: [Spring Boot layered Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html), [Nginx WebSocket forwarding](https://nginx.org/en/docs/http/websocket.html), [Compose networks](https://docs.docker.com/reference/compose-file/networks/), [FastAPI lifespan](https://fastapi.tiangolo.com/advanced/events/), [GHCR authentication/publication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
