# Separate execution-host prerequisite

No isolated Judge0-compatible host has been supplied or certified. The application Compose deliberately contains no privileged judge containers and mounts no Docker socket into the backend. Do not execute student code on the application host or point production at a public demo.

Before enabling execution, the operator must record the selected implementation/version and immutable image digests, its actual engine-specific database/queue/cgroup/kernel requirements, host isolation/network egress, authentication/TLS, compiler catalog and resource enforcement tests. Judge0-compatible HTTP alone is not evidence of host isolation. Use a dedicated host/network and follow that exact engine's primary deployment documentation; no generic Redis/PostgreSQL assumptions or fabricated language IDs are encoded here.

Start from `docs/sandbox.md`, populate an external copy of `deploy/runtime-catalog.yaml.example` with verified provider names/versions/IDs/entry points, and configure endpoint/key outside Git. Rehearse compiler/runtime errors, TLE/MLE/output/process/file/network limits and worker restart/lease recovery on synthetic code. Confirm hidden outputs never reach student endpoints/prompts/logs before enabling the production flags.

Real execution CI should run only on a trusted isolated runner with environment-scoped credentials and never on PR jobs. Image pins/manifests and that live test job remain pending the actual engine/host inputs. The current CI covers the adapter protocol and durable state machine with local mocks/Testcontainers, plus the application container mesh.
