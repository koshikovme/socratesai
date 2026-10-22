# Container delivery and deployment

The application package contains `frontend-nginx`, `backend`, `policy-api` and PostgreSQL 16. Only Nginx publishes host ports 80/443. Nginx serves the production SPA and proxies `/api` and all `/ws`/SockJS transports to the backend on the same HTTPS origin. The database and model service have no host ports; their `backend-net` is internal. The backend also joins `frontend-net` for edge traffic and optional outbound provider/Judge0 calls.

This is application packaging and release automation. A certified separate execution host, public DNS/TLS, hosting access, backups and institutional retention decisions remain deployment prerequisites. The repository does not claim a live production release or a verified Judge0 installation.

## Build and local verification

Verified tools: Java 21, Node 22.23.1/npm 10.9.8, Python 3.12, Docker Engine/BuildKit and Docker Compose with health-aware `up --wait`. Dockerfiles pin their base images and Dockerfile frontend by digest. Maven wrapper distribution has a SHA-256 checksum; `package-lock.json`, hashed serving/test Python locks and `ml/models.manifest.json` are committed.

```sh
docker build -t socratesai/backend:delivery-test .
docker build -t socratesai/frontend:delivery-test socratesai-frontend
docker build -t socratesai/policy-api:delivery-test ml
python3 scripts/delivery/context_check.py
bash scripts/delivery/smoke.sh
bash scripts/delivery/scan.sh
```

Put Node 22 on PATH for the smoke script. It creates a uniquely named disposable Compose project, new database volume, random credentials and a short-lived self-signed TLS certificate. It binds random ports to loopback, verifies TLS/SPA/API/auth/denied origins/STOMP/Flyway/compiler absence/private ports and the non-superuser DB role, then deletes **only its own project and volume**. It never uses shared databases 21/22 or executes student code. The application images start without developer source/cache/dataset mounts. The context check uses synthetic forbidden files in temporary folders and tests the actual Docker ignore rules with BuildKit.

`scan.sh` uses a digest-pinned Trivy image, produces JSON reports/CycloneDX SBOMs under ignored `.delivery/scans/`, and blocks fixed HIGH/CRITICAL advisories in the three built application images. The stock database image is scanned separately and reported without hiding its bundled `gosu` Go-library findings; see the Step 8 report for the current count and required pre-production review. It runs as UID 70 and does not invoke that privilege-switch helper in this configuration. This is not a blanket CVE exemption or a claim of zero findings for the entire stack. Unfixed or lower severity advisories are not covered by that gate. Scan databases are current at scan time, so later scans may discover new issues and require patched pins. The scanner's Docker socket access is for trusted tooling on disposable CI runners/operator machines; application containers never receive that socket.

The backend image uses a Java 21 JDK only for the build, caches Maven dependencies separately, and copies extracted Boot layers into a non-root JRE image. Both `javac` and `jdk.compiler` are absent from the runtime. The frontend uses a Node build stage and a non-root Nginx runtime with two workers and bundled Monaco assets. The Python image uses wheel-only, hash-verified serving dependencies, exactly two saved models and a non-root runtime. Models are verified and loaded atomically during lifespan startup; `/ready` and `/health` return 503 until both are loaded. No model training/download occurs at container startup.

## Configure an installation

Copy `.env.example` to an external environment file or a local ignored `.env`, restrict it to the operator (`chmod 600`) and fill every mandatory setting. There are no usable default passwords or production signing secrets.

- Set `BACKEND_IMAGE`, `FRONTEND_IMAGE` and `POLICY_IMAGE` to the **digest references from a successful release manifest**, e.g. the actual `ghcr.io/...@sha256:...` values. The template release tag is an instruction, not an existing image.
- Set `APP_DOMAIN` to the DNS hostname and `APP_PUBLIC_ORIGIN` to its exact HTTPS origin, including a nonstandard port only if it is actually public. Origin wildcards, credentials, query strings and path suffixes are rejected. Compose supplies that same origin to CORS/STOMP and the edge.
- Generate independent random `POSTGRES_ADMIN_PASSWORD`, `POSTGRES_PASSWORD` and base64 `APP_JWT_SECRET` (at least 64 decoded random bytes). The operator role defaults to the nonsecret name `cluster_admin`; `POSTGRES_USER` names the separate application role. Do not use the same role for both.
- Set `TLS_DIRECTORY` to an external directory containing `fullchain.pem` and `privkey.pem`. It is mounted read-only to Nginx. The directory and key must be readable by container UID/GID 101: use a restricted directory owned by the operator/group 101 and a key with mode 0640, or equivalent ACLs. Never bake keys into an image or copy them into the repository. Nginx fails if they are absent/unreadable. Certificate issuance and renewal belong to the operator; after atomic replacement, test/reload Nginx with `docker compose ... exec frontend-nginx nginx -t` and `nginx -s reload`.
- Set `APP_RUNTIME_CONFIG_FILE` to a nonsecret external runtime-catalog YAML. The empty example is valid only while Judge0 is disabled. Never invent language IDs/compiler versions: verify the separate server's installed catalog and then fill its runtime bindings. See `docs/sandbox.md` and `deploy/sandbox/README.md`.
- Both `APP_JUDGE0_ENABLED` and `APP_JUDGING_ENABLED` default to false until a certified server exists. Real runs/reference verification/publication requiring execution are unavailable in that mode. Enable judging only with the Judge0 endpoint, header/key, verified runtime catalog and tested resource limits. Production allows plain HTTP only through the explicit sandbox opt-in for a trusted private channel; use TLS otherwise.
- LLM providers default off. Enabling one requires its real API key, HTTPS base URL and explicit model name. Otherwise feedback uses templates; ML failure still falls back to rules. The model API stays private and is loaded even when its backend policy switch is off.
- For the first admin, temporarily set `APP_ADMIN_BOOTSTRAP_ENABLED=true` plus external email/full name and a unique password of at least 16 characters. After successful provisioning, disable bootstrap and unset the password, then recreate the backend. Further role changes use the audited admin API/UI. Public signup never provisions staff.

Backend production validation runs before bean creation/Flyway/DB connections. It rejects missing/weak signing material, placeholder passwords, invalid origins/endpoints, enabled providers without configuration, invalid policy thresholds, secret-bearing database URLs, SQL/request debug logging and an enabled worker without Judge0. Existing bound configuration classes validate runtime selections, quotas, lease/deadline/backoff/retention limits. Error messages identify setting names and do not echo values/causes. Production disables OpenAPI and exposes only health through Actuator; the edge blocks Actuator routes.

## Start, update and verify

From a deployment checkout containing the Compose file and nonsecret init/runtime files:

```sh
docker compose --env-file /absolute/path/production.env -f docker-compose.prod.yml config --quiet
docker compose --env-file /absolute/path/production.env -f docker-compose.prod.yml pull
docker compose --env-file /absolute/path/production.env -f docker-compose.prod.yml up -d --no-build --wait --wait-timeout 180
docker compose --env-file /absolute/path/production.env -f docker-compose.prod.yml ps
```

Use `config --quiet`: plain `config` prints expanded secrets. Never publish those values, full environment inspections or private interaction data in CI artifacts.

Health checks: backend `/actuator/health/readiness` includes readiness state plus DB; liveness excludes DB/model dependencies. Policy health uses `/ready`. Nginx checks its local HTTP `/healthz`. The reverse proxy uses a dynamic Docker DNS resolver, forwards HTTPS/host headers, disables caching for API/WS, preserves native API paths and disables SockJS buffering. HTTP redirects to the configured HTTPS origin; immutable asset paths are cached and HTML is revalidated. Forwarded chains from clients are overwritten by the sole trusted edge. No backend/DB/ML ports should be published by additional host firewall/proxy rules.

On a **new** PostgreSQL volume, the initialization hook creates the application login without SUPERUSER/CREATEDB/CREATEROLE and transfers ownership of its database/schema so Flyway can migrate. The hook suppresses statement logging while assigning the password. It does not run on an existing volume. Existing installations require an operator-reviewed role/ownership migration using current credentials; editing environment variables does not rotate existing DB passwords.

Resource defaults are explicit, bounded starting settings, not measured production capacity: backend 1.5 GiB/2 CPUs, policy 768 MiB/1 CPU, database 1 GiB/1 CPU, edge 128 MiB/0.5 CPU. Root filesystems are read-only, application users are non-root, capabilities are dropped, temporary paths are tmpfs, and logs rotate. PostgreSQL data is the only persistent application volume. Back up that volume/database before any real update; do not run `down -v` on an installation.

## CI and release promotion

`.github/workflows/production-delivery.yml` replaces the earlier duplicate test workflow. It runs on main/develop pushes, PRs, manual validation and `v*.*.*` tag pushes:

1. Java 21 `mvnw verify` with coverage gates and actual Docker/Testcontainers/Flyway PostgreSQL tests; failure to access Docker fails the job.
2. Node 22 locked install, ESLint, frontend coverage/build and Chromium protocol-fixture journeys.
3. Python hash-locked test dependencies, saved-model inference/contracts, readiness and syntax checks.
4. Digest-pinned actionlint plus shell syntax and real build-context exclusion tests.
5. Builds all three images with isolated BuildKit caches, cold-volume production Compose/TLS smoke, Trivy gates and SBOMs. PR jobs have no release/environment credentials or privileged execution-sandbox access.
6. On a valid version tag pointing to main history, promotes **the same tested image tar artifact** to GHCR with version and commit tags, then uploads immutable image references plus commit/model hashes. Existing version tags are not overwritten; `latest` is not promoted automatically.

Every action is pinned to a reviewed commit SHA. Permissions default to `contents:read`; only the trusted `publish` job has `packages:write` and uses `GITHUB_TOKEN` for GHCR. Tag builds are not cancelled by later commits. Reports live 7 days, image tar artifacts 3 days, release manifests 30 days. Configure the repository's `release` environment with required reviewers, allowed version tags and package permissions before using it as an approval gate; declaring the environment in YAML does not configure those protections by itself. Protect main and release tags in repository settings.

A source push starts validation; it does not deploy a host. A release tag publishes only after all gates and environment policy succeed. No tag, registry publication or remote GitHub run success is claimed by writing the workflow. Download the successful release manifest and promote its exact digest set into the installation's external environment file. Keep it with a backup identifier, current Flyway schema/model hashes and smoke evidence.

## Rollback and remaining operations

Before a real promotion, back up PostgreSQL, record the existing image digests/schema and acquire the installation's deployment lock. Keep one operator/deployment process per host; Flyway serializes its own migration history, while GitHub concurrency serializes release workflows. Rehearse on an isolated restored volume first. If a new application fails, use previous digests only after checking their compatibility with the current additive schema. There is no automatic Flyway downgrade and no volume deletion. A schema restore requires a separate approved recovery procedure and its corresponding application images.

Staging/production deployment identities, host-specific locks, backup/restore schedules, certificate renewal, live Judge0 certification, institutional long-term source retention and a hosted full browser/judging rehearsal remain Steps 8–9 operational work. They need the chosen host/access and verified execution server; this delivery does not invent SSH targets, secrets, runtime IDs or production URLs.
