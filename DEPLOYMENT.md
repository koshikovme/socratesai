# Production deployment and operations

The authorized installation is **https://socratesai.duckdns.org**. The application runs on
`89.126.199.55` and real compilation/execution on the separate `89.126.199.58` VM.
The repository owner operates both hosts. Credentials are held in the ignored, protected
`.delivery/production-access.md` and external host configuration; none belong in this document.
The verified automatic release is **v0.9.3** (`c53bf74`); its permanent nonsecret digest
manifest is [deploy/releases/v0.9.3.json](deploy/releases/v0.9.3.json).
Dated checks, image identifiers and release evidence are in
[tasks/socrates-evolution/result-mentor-v2.md](tasks/socrates-evolution/result-mentor-v2.md).
The original installation/recovery evidence remains in
[result-9-production.md](tasks/socrates-evolution/result-9-production.md).

## Layout and boundaries

Nginx serves the Vue SPA, redirects HTTP to HTTPS, and proxies `/api` and all `/ws`/SockJS
transports on one origin. Only edge ports 80/443 and operator SSH are public on the main VM.
Backend, PostgreSQL and FastAPI have no public host ports. The internal backend network
contains the database and model service. The backend uses a JRE without `javac`/`jdk.compiler`.

The dedicated executor exposes its authenticated API only on WireGuard `10.93.0.2:2358`;
the application peer is `10.93.0.1`. The tunnel uses the provider's private network.
Student programs have no network access, application credentials, repository mounts or
Docker socket. Only the executor worker has the privileges/cgroups required by Isolate.
The main VM rejects unsolicited inbound/routed `wg0` traffic. See the actual engine pins,
three-language catalog, startup and boundary gates in [deploy/sandbox/README.md](deploy/sandbox/README.md).
The old official Debian 10 Judge0 image is rejected and must never be a rollback target.

Application resource limits are backend 1536 MiB/2 CPUs/PID 256, model 768 MiB/1 CPU/PID 96,
PostgreSQL 1024 MiB/1 CPU/PID 128 and edge 128 MiB/0.5 CPU/PID 64. Limits share the main
2-vCPU/4-GiB host; they are ceilings, not reserved capacity. The one-vCPU executor uses one
worker with a 2-GiB/PID-256 ceiling. API/Redis/database have separate ceilings in its Compose.
Both hosts have 1-GiB swap, Docker log rotation and bounded journald retention.

Named volumes retain application PostgreSQL, the model bundle cache, safe operations logs,
executor PostgreSQL and Redis AOF. Model-cache volume names include the manifest hash;
the loaded files are checksum-verified and read-only. Never run `down -v` on these projects.

## Main-host commands

Connect using the operator SSH key, then use the installed release directory:

```bash
ssh -i /path/to/operator-key root@89.126.199.55
cd /opt/socratesai/current
compose=(docker compose --env-file /opt/socratesai/private/production.env \
  -f docker-compose.prod.yml -f deploy/host/compose.acme.yml)
"${compose[@]}" config --quiet
"${compose[@]}" ps
bash deploy/host/health.sh
curl --fail --silent --show-error https://socratesai.duckdns.org/healthz
```

For an already reviewed digest/config change during maintenance:

```bash
"${compose[@]}" up -d --no-build --wait --wait-timeout 180
bash deploy/host/health.sh
```

Use `config --quiet`. Plain `config`, full `docker inspect`, environment dumps, request-body
logging and shell tracing can reveal secrets. Inspect only selected state fields and safe
job IDs/statuses. Do not paste raw provider/exception output or student records into issues.

The current application directory is `/opt/socratesai/releases/20261105-2`, linked by `current`.
Runtime files are `/opt/socratesai/private/production.env`, `runtime-catalog.yaml` and `tls/`.
The environment file is mode 0600. TLS keys are 0640 root:101 in a 0750 directory.
The application database role `socrates` has no SUPERUSER/CREATEDB/CREATEROLE rights.
`cluster_admin` is an independent operator role, never used by backend connections.
There are **13** successful Flyway migrations, including V9–V12 and V13 phrasing quotas.

## Provisioning a replacement main host

Use `deploy/host/bootstrap.sh` only on an agreed, dedicated Ubuntu VM. It installs Docker,
firewall/SSH prerequisites, log rotation and host limits. Install operator public keys before
using `private-link.sh`, which disables password login. Configure private link keys on each
host without transferring private keys. On the main peer additionally deny incoming and
routed `wg0` initiation; verify established application-to-executor traffic still works.
Allow only 22/80/443 publicly and WireGuard UDP only from the peer's private address.

Restore the exact nonsecret release/Compose files. Copy `.env.example` to the external
production environment, mode 0600, and fill mandatory settings. Use independent OS-CSPRNG
passwords, at least 64 random decoded bytes for JWT, exact HTTPS origin, real TLS files,
verified runtime catalog and digest-pinned images. No HSM certification is implied.
The PostgreSQL initialization hook creates the nonsuperuser application role on a **new**
volume only. Existing roles need a reviewed SQL password/ownership change; editing an env
file alone does not rotate existing database credentials.

Bootstrap the first administrator with external `APP_ADMIN_BOOTSTRAP_*` settings only.
After creation, disable bootstrap and remove its email/password/name before recreating the
backend. This is already done in production. Public signup creates STUDENT; further staff
roles use the audited admin UI/API. No live passwords are stored in examples or images.

Both execution flags default off. Enable them only after the isolated executor is certified.
Production permits the explicit private-HTTP opt-in solely inside the authenticated WireGuard
link. Do not point it at a public demo, another developer's database, or the local demo fixture.

## TLS and DNS

DuckDNS points the application hostname to `89.126.199.55`. The owner confirmed rotation of
its previously exposed token; the token is not required in the application environment.
Let's Encrypt certificates are installed. ACME challenges use `/var/lib/socrates-acme`,
mounted read-only into the edge. The deploy hook copies real certificate files into `tls/`,
tests Nginx and reloads it; this avoids broken Certbot symlinks inside the container.

```bash
systemctl status certbot.timer --no-pager
certbot renew --dry-run
curl --head http://socratesai.duckdns.org/
```

The redirect must be 308 to HTTPS. A renewal dry-run has passed. After a future certificate
change, check Nginx configuration and reload through `deploy/host/renew-certificate.sh`,
which Certbot invokes with `RENEWED_LINEAGE`. Do not manually print or commit private keys.

## CI, registry and automatic delivery

`.github/workflows/production-delivery.yml` validates main/develop pushes, PRs, manual runs
and release tags. It runs Java 21 verification/coverage with real disposable PostgreSQL and
Flyway; Node 22.23.1 frontend tests, lint, production build and Chromium fixtures; Python
locked inference/readiness contracts; shell/actionlint and four build-context exclusions.
It builds backend, edge, model and supported Judge0 images, starts the cold application mesh,
checks executor startup without running any student program, and scans images/SBOMs.

The fixed HIGH/CRITICAL gate applies to those four built images. Redis is separately checked
on the executor. The stock PostgreSQL image has recorded findings in its unused bundled
`gosu` Go helper; both database containers run directly as UID 70, read-only with capabilities
dropped. Findings are reported, not silently excluded or called an all-stack zero. Review
new advisories and patch pins regularly; a dated scan is not a security guarantee.

v0.9.2 correctly stopped at the security gate on the APT/libapt-pkg6.0 advisory
CVE-2026-109518. v0.9.3 installs Debian's 2.6.2 fix in the model and executor images;
all four built images passed the fresh gate before publication. Do not restore the earlier
2.6.1 images to bypass a failed scan. The scan summary prints advisory/package data only.

Repository settings already contain:

- Secrets `PRODUCTION_SSH_KEY` and `PRODUCTION_KNOWN_HOSTS`.
- Variables `PRODUCTION_HOST=89.126.199.55` and `PRODUCTION_AUTO_DEPLOY=true`.
- Workflow environments `release` and `production`, created when referenced jobs run.

The deployment key is a dedicated unencrypted OpenSSH private key stored only in the secret
and the operator's protected files. Its server key is forced to `receive-release.sh` and
permits only `health` and validated `promote`, with forwarding disabled. Operator access
uses a separate key. An empty Environments page before the first job is normal; declaring
an environment does not configure reviewers, branch protection or tag rules automatically.
Those optional repository governance settings remain the owner's responsibility.

A main push validates and checks the restricted SSH identity; it does not replace containers.
To release an already reviewed main commit, create a **new** semantic tag and push it:

```bash
git tag -a vX.Y.Z -m 'SocratesAI production release vX.Y.Z' <reviewed-main-commit>
git push origin vX.Y.Z
```

The version must be real and unused. Tags must point to main history. CI promotes the exact
verified image artifact to GHCR, records immutable references and passes the three application
digests to the restricted host command. Judge0 has a separate manifest: compiler changes
require live isolation certification and explicit executor maintenance, not automatic rollout.
The registry token exists only in the deployment input/temporary Docker configuration and
is deleted afterward. No permanent registry credential is installed on the host.

Host promotion acquires the deployment lock, checks schema compatibility, pulls digests,
creates an encrypted backup, preserves `previous.env`, replaces the application digest set,
waits for readiness and checks HTTPS/schema. Failure with an unchanged schema restores the
previous compatible digest set. `/opt/socratesai/private/current-release.json` contains only
public release identifiers. Unattended migration to a different schema version is rejected.
GitHub permissions are least-privilege; PRs receive no deployment or execution credentials.
Reports expire after 7 days, image artifacts after 3 and release manifests after 30: retain
operator release evidence and backups independently. Never overwrite a published tag.

## Backups and restore

The main-host timer makes a custom-format PostgreSQL dump at 02:30 UTC plus up to ten minutes,
streaming it directly into AES-256-GCM OpenSSL CMS encryption. Only the recipient public
certificate is installed on the server. The private recovery key stays on the operator
workstation. Host backups and SHA-256 files retain 14 days. No plaintext dump is stored there.

```bash
systemctl list-timers socrates-backup.timer socrates-health.timer --no-pager
systemctl start socrates-backup.service
```

The workstation timer copies encrypted backups at 03:00 UTC plus up to five minutes, with
catch-up when the workstation is online/logged in. It verifies SHA-256 and uses strict SSH
host-key verification. It has already completed successfully. It is not an always-on remote
backup service; if the workstation is off, copies wait. Protect a separate recovery-key copy
outside the VPS and this repository. Local backup retention is operator-managed.

```bash
bash scripts/delivery/install_backup_fetch.sh
systemctl --user status socrates-backup-fetch.timer --no-pager
python3 scripts/delivery/fetch_backups.py --identity-file .delivery/ssh/deploy_ed25519 \
  --destination .delivery/production-backups
```

An encrypted production snapshot was decrypted temporarily and successfully restored into
a disposable `--network none` PostgreSQL container owned by a nonsuperuser role. Schema and
row counts were checked; the container and plaintext dump were removed afterward. For an
incident, preserve the original volume and restore into a **new**, isolated database before
repointing compatible application images. Do not restore over the only live copy.
Example decryption, run only on the protected operator workstation:

```bash
umask 077
openssl cms -decrypt -binary -inform DER -in <encrypted-backup.dump.cms> \
  -inkey .delivery/backup-recovery.key -out <temporary-private-dump>
```

Use `pg_restore --no-owner --no-privileges --exit-on-error` into the isolated database/schema
owned by its nonsuperuser restoration role. Verify Flyway history and expected counts without
printing private rows. Remove plaintext after verification. There is no automatic Flyway
schema downgrade; database restoration is a separate recovery operation.

The patched execution image is also copied off-host as
`.delivery/production-images/judge0-apt262.tar.gz`, with SHA-256
`dc04cdc1df73d64e75c70ecc76afca00a2190f0300a69e9de104caf828e53cc3`.
Its archive checksum and immutable Docker image ID were verified before activation on the
dedicated VM. Three real language runs, nine boundary probes and all three unfinished starters
passed afterward. The old archive is historical evidence, not a current security rollback target.
On a replacement **dedicated execution VM**, after verifying that archive checksum:

```bash
docker load --input judge0-apt262.tar.gz
docker image inspect sha256:c2853099f079f4d9031173ea21dab5ac65fec4618f2daa63adb1b1ce2f57aca1 \
  --format '{{.Id}}'
cd /opt/socratesai
docker compose --project-name socrates-judge \
  --env-file private/judge0.conf --env-file private/images.env \
  -f compose.yml config --quiet
docker compose --project-name socrates-judge \
  --env-file private/judge0.conf --env-file private/images.env \
  -f compose.yml up -d --no-build --pull never --wait --wait-timeout 180 server worker
```

Set `JUDGE_IMAGE` in the protected `images.env` to the exact `sha256:c285…` image ID recorded
above. This is a locally certified Docker image ID, not a registry manifest digest. Preserve
the existing named database/Redis volumes, project name and both environment files. A new
host also needs the pinned database/Redis images and reviewed initialization configuration.
Start with `--pull never`, the correct kernel/cgroups, private link and fresh isolation checks.
Do not substitute the rejected legacy image or assume
that the separately published CI execution candidate has already been certified on this host.
No runtime credentials are present in this image archive. Keep the archive/checksum alongside
operator recovery instructions; do not copy application secrets onto the executor.

## Incident runbook

| Symptom | Action and verification |
| --- | --- |
| New release fails readiness | Promotion restores previous images if schema is unchanged. Inspect safe phase code and `previous.env` privately; check four services and public `/healthz`. Do not retry unknown provider POSTs. |
| Need a manual compatible rollback | Acquire `/opt/socratesai/private/deployment.lock`, make an encrypted backup, restore reviewed prior image references in a private env copy, start with the same Compose/volumes, check readiness/catalog/schema. Never return to the rejected legacy executor image. |
| Backend crashes with RUNNING jobs | Restart the backend, wait for lease expiry/reclaim, confirm generation increases and the same persisted tickets are polled. Verified with two cases, no duplicate remote executions or student attempt. |
| Executor unavailable | Stop admission/disable judging during maintenance. Existing jobs reach bounded SYSTEM_ERROR rather than false ACCEPTED. Restore WireGuard/API/worker readiness before enabling. Preserve uncertain ticket state. |
| Model unavailable | Gemini supplies classification and hints when the Python classifier is unavailable; Java/C++ always use Gemini. Judging and progress remain independent. Restore `/ready` after complete model load/checksum validation. |
| Gemini error or budget exhausted | Native AI returns an explicit unavailable/allowance error; it never substitutes a rule hint. Do not retry with unbounded prompts. Review safe request IDs and durable quota counts only. |
| TLS renewal failure | Check DNS, port 80 and ACME webroot, run the dry-run, inspect certificate metadata, deploy hook and Nginx config. Keep keys out of logs. |
| Disk reaches 80% or host health fails | `socrates-health` fails and records a safe status. Check selected volume/image sizes and backup/log retention. Delete only confirmed unused images/build caches; never database volumes. |
| DB loss | Preserve failed storage, restore encrypted backup into an isolated new volume, verify schema/data, then switch compatible images/config during maintenance. |
| Credential exposure | Revoke/rotate at its authority; update both sides for DB/executor credentials, recreate affected containers and invalidate sessions when rotating JWT. Never expose values while diagnosing. |

The health timer checks all four application containers, public HTTPS and disk every minute.
Safe status logs use the `socrates-operations` named volume and logrotate. Docker logs rotate
at 10 MiB × 3; journal retention is 150 MiB/7 days. Failed timers/services are visible through
systemd. External email/SMS incident routing is **not configured**; the owner must monitor
these checks or select a notification destination before relying on unattended operation.

## Acceptance and capacity boundaries

All live test identities are explicitly synthetic. Use `scripts/delivery/live_acceptance.py`,
`live_boundaries.py`, `live_runtime_matrix.py`, `live_browser.cjs`, `live_capacity.py` and
`live_recovery.py` only on the hardcoded authorized
installation, with private access files. Recovery/outage helpers intentionally interrupt the
owned services and must run in a maintenance window. Student source is executed only remotely.
The browser rehearsal uses actual HTTPS/WSS/Monaco and real judging, separately from CI fixtures.
From the operator checkout, with Node 22.23.1, installed frontend Playwright dependencies and Chrome:

```bash
python3 scripts/delivery/live_acceptance.py --access-file .delivery/production-access.json --run-label v0.9.3
python3 scripts/delivery/live_runtime_matrix.py --access-file .delivery/production-access.json
node scripts/delivery/live_browser.cjs --access-file .delivery/production-access.json
```

The release label gives a stable new idempotency namespace for that rehearsal. These scripts
create synthetic runs/attempts; they are not read-only health checks. Preserve existing evidence
before repeating capacity/recovery rehearsals.

The v0.9.3 bounded rehearsal received 30 genuine code-only Gemini hints over 30 authenticated
WebSockets, then completed 30 short Python sample runs with no graded attempts. POST 202
p95 was 3,159.2 ms; maximum completion was 37.1 seconds. These are one synthetic rehearsal's
observations. They do not establish throughput for arbitrary compiler-heavy classes or learning
gains. Keep one executor worker and the recorded quotas initially; inspect queue pressure
before a class. The collection currently contains a verified introductory problem, not a
complete curriculum. Graded-source retention/deletion and external alert routing remain owner
operational decisions; no automatic historical submission deletion is invented.

Gemini uses the funded project and `gemini-3.5-flash-lite`. Durable caps are 3,000 monthly global
requests, 60/minute and 30/student/day, with at most 2,048 counted input tokens and 128 output
tokens/request. At the reviewed input/output prices, the configured upper estimate is $2.8032
per month. This excludes taxes, price changes and calls made with the same key outside this
application; it is not a Google billing-account hard cap. Native quota failure is explicit; grading remains available and no fake rule hint is shown. The conservative monthly maximum remains $2.8032.

## Local build/demo

Java 21, Node 22.23.1, Python 3.12 and Docker/Compose are the verified tools. Build contexts
exclude environment files, keys, local datasets, experiments and paper sources. Model artifacts,
Maven/npm/Python locks and compiler/base-image pins are committed. A cold CI build needs `unzip`
for the checksum-pinned Maven ZIP; do not change the checksum to hide a failed download.

```bash
docker build -t socratesai/backend:delivery-test .
docker build -t socratesai/frontend:delivery-test socratesai-frontend
docker build -t socratesai/policy-api:delivery-test ml
docker build -t socratesai/judge0:delivery-test deploy/sandbox
python3 scripts/delivery/context_check.py
bash scripts/delivery/smoke.sh
bash scripts/delivery/sandbox_startup_smoke.sh
SCAN_SANDBOX_IMAGE=true bash scripts/delivery/scan.sh
```

Smoke checks create only disposable loopback containers/volumes with synthetic credentials
and TLS. They never use databases 21/22 or execute student programs. For an explicitly synthetic
local demonstration, run `bash scripts/demo/demo.sh up`, open **http://localhost:8088**, and
follow [deploy/demo/README.md](deploy/demo/README.md). Its fixture does not compile programs
and must never be exposed as the production executor.
