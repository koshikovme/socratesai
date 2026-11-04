# Dedicated execution host

The application and executor run on separate authorized Hoster.kz VMs. Untrusted compilation
and execution belong exclusively on this VM. Never add the worker to the application Compose,
mount a Docker socket into the application, or expose the execution API publicly.

## Implementation and verified catalog

`Dockerfile` packages pinned Judge0 source `d0632cffaaf4fcdbf83a83e7a55ed9baf8a86db6`
and its Isolate `ad39cc4d0fbb577fb545910095c9da5ef8fc9a1a`. Download checksums are mandatory.
The standard Judge0 job/Isolate implementation remains responsible for execution. The small
`modernize.rb` compatibility patch supports the frozen Ruby/Rails dependency set, removes
embedded configuration, filters sensitive fields and preserves execution signals.
This is project-maintained packaging, not an upstream Judge0 release.

The historical official 1.13.1 container was rejected: its Debian 10 runtime and dependency
scan had 3,632 HIGH/CRITICAL findings with available fixes. Do not restore that image.
The replacement uses Debian 12, Ruby 3.4, Rails 8.1, a checksum-bearing Bundler lockfile,
Python 3.12, Java 21 and GCC 12. Only these three seeded runtimes are enabled:

| Language key | Provider ID | Actual provider name | Entry point |
| --- | --- | --- | --- |
| `python3` | 1001 | Python (3.12.16) | `main.py` |
| `java` | 1002 | Java (OpenJDK 21.0.13) | `Main.java` |
| `cpp17` | 1003 | C++ (GCC 12.2.0) | `main.cpp` |

These are observations from this deployment, not universal Judge0 IDs. Python syntax is
checked by isolated `py_compile` before execution. Java uses a bounded heap and C++ uses C++17.
Changing the image requires repeating catalog and boundary verification and publishing new
problem versions; old immutable submissions retain their original runtime provenance.
See `production-runtime-catalog.yaml` and the production report for the certified image.

## Host and trust boundary

The dedicated Ubuntu 22.04 VM runs kernel `5.15.0-202-generic`, Docker 29.9.3 and cgroup v1.
The pinned Isolate needs that cgroup setup. On this host it is selected through
`systemd.unified_cgroup_hierarchy=0` in `/etc/default/grub.d/99-judge0-cgroup.cfg`, followed
by `update-grub` and a controlled reboot. Verify `docker info` before starting workers.
Do not change the application host's cgroup mode.

Only the worker is privileged, with the host cgroup namespace as required by Isolate.
The API is UID 1000, read-only, has all capabilities dropped and forbids privilege escalation.
PostgreSQL and Redis also use explicit unprivileged UIDs, bounded memory/PIDs and named volumes.
The database application role is `judge_runtime` with no superuser/create-role/create-DB rights;
the separate database operator credential never enters the API or worker configuration.

The API binds `10.93.0.2:2358` on WireGuard. The application connects from `10.93.0.1` using
an independent random `X-Auth-Token`. The underlay uses the provider's private addresses.
The execution network is Docker-internal. Sandbox programs have network disabled, cannot see
host configuration or provider environment variables, and cannot reach the application VM.
The application host also denies unsolicited traffic on `wg0`. DNS for the executor does not
make its API publicly reachable.

## Reproduce packaging and startup

Build only trusted engine source on an operator machine or the dedicated VM:

```bash
docker build -t socratesai/judge0:operator-build deploy/sandbox
SANDBOX_IMAGE=socratesai/judge0:operator-build bash scripts/delivery/sandbox_startup_smoke.sh
```

The startup smoke executes `/bin/true` only, with synthetic configuration, no network,
read-only filesystem and no elevated capabilities. CI builds/scans the image and checks this
contract; CI never executes student programs or runs privileged sandbox workers.

For a **new** dedicated host, install Docker, the verified kernel/cgroups, firewall and
WireGuard first. Copy `compose.yml` to `/opt/socratesai/compose.yml`, copy
`../postgres/10-application-role.sh` to `/opt/socratesai/init-application-role.sh`, and create
`/opt/socratesai/private` with mode 0700. Use the two `.example` files as templates.
Generate each password/auth token with `openssl rand -hex 32` and signing material with
`openssl rand -hex 64`; write directly into protected files, never chat, shell history or Git.
The runtime role/password and Redis password must agree between the two files.
Set `JUDGE_IMAGE` to the verified immutable image digest, not `latest`.
Use mode 0600 for `images.env` and 0640 root:999 for `judge0.conf` so UID 1000:999 can read it.

```bash
cd /opt/socratesai
docker compose --project-name socrates-judge \
  --env-file private/images.env -f compose.yml config --quiet
docker compose --project-name socrates-judge \
  --env-file private/images.env -f compose.yml up -d --wait --wait-timeout 180
```

The initializer creates the runtime role only for a new database volume. Existing clusters
need a reviewed role/ownership migration; never delete a volume to change a password or role.
Retain application backup/release provenance separately from short-lived provider records.

## Release gates and operations

Keep application `APP_JUDGE0_ENABLED` and `APP_JUDGING_ENABLED` off until authentication,
catalog, all three languages, compile/runtime failures, time/memory/output/process bounds,
filesystem/environment/network isolation, hidden-output projection and restart recovery pass.
Network request flags alone do not certify isolation. Scan the complete image and inspect
upstream advisories before each upgrade. A zero fixed HIGH/CRITICAL scan is a dated check,
not a proof of absence of vulnerabilities.

Provider rows contain source and test inputs. The production hourly retention job removes
rows older than 24 hours; execution PostgreSQL/Redis storage is private to this host.
Worker count is one on the current one-vCPU VM. Increase it only after measuring queue and
resource pressure. Rotate execution credentials on both hosts together without printing them.
For loss of the worker, stop admission, inspect safe job IDs/statuses and let persisted ticket
polling/leases reconcile; never blindly replay uncertain POST requests.

See [adapter contracts](../../docs/sandbox.md), [deployment runbook](../../DEPLOYMENT.md),
and [production verification](../../tasks/socrates-evolution/result-9-production.md).
