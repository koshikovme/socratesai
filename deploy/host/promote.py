"""Promote only digest-pinned artifacts from this repository; credentials never enter diagnostics."""
import fcntl
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path('/opt/socratesai')
ENV = ROOT / 'private/production.env'
COMPOSE = ['docker', 'compose', '--env-file', str(ENV), '-f', 'docker-compose.prod.yml', '-f', 'deploy/host/compose.acme.yml']


def run(arguments, **kwargs):
    result = subprocess.run(arguments, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=240, **kwargs)
    if result.returncode:
        raise RuntimeError('Release command failed; private command output suppressed')
    return result.stdout


def schema():
    value = run(COMPOSE + ['exec', '-T', 'postgres', 'sh', '-c',
        'exec psql -U "$APP_DATABASE_USERNAME" -d "$POSTGRES_DB" -Atqc "SELECT max(version::integer) FROM flyway_schema_history WHERE success"'])
    return int(value.decode().strip())


def main():
    os.umask(0o077)
    request_path = Path(sys.argv[1])
    try:
        raw = request_path.read_bytes()
    finally:
        request_path.unlink(missing_ok=True)
    if len(raw) > 16384:
        raise ValueError('Oversized promotion request')
    request = json.loads(raw)
    images = request['images']
    if set(images) != {'backend', 'frontend', 'policy-api'}:
        raise ValueError('Invalid image set')
    for name, reference in images.items():
        if not re.fullmatch(r'ghcr\.io/koshikovme/socratesai/' + name + r'@sha256:[a-f0-9]{64}', reference):
            raise ValueError('Invalid image reference')
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', request['version']):
        raise ValueError('Invalid release version')
    if not re.fullmatch(r'[a-f0-9]{40}', request['source_commit']):
        raise ValueError('Invalid source identifier')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9-]{0,63}', request['registry_user']):
        raise ValueError('Invalid registry identity')
    os.chdir(ROOT / 'current')
    with (ROOT / 'private/deployment.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        previous_schema = schema()
        # A schema change requires a separate reviewed restore/compatibility rehearsal.
        if type(request['schema_version']) is not int or request['schema_version'] != previous_schema:
            raise ValueError('Schema upgrade requires operator promotion')
        previous = ENV.read_bytes()
        with tempfile.TemporaryDirectory(prefix='registry-', dir=ROOT / 'private') as directory:
            docker_environment = {**os.environ, 'DOCKER_CONFIG': directory}
            run(['docker', 'login', 'ghcr.io', '--username', request['registry_user'], '--password-stdin'],
                input=request['registry_password'].encode(), env=docker_environment)
            for reference in images.values():
                run(['docker', 'pull', reference], env=docker_environment)
            model_manifest = run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                                  '--entrypoint', 'cat', images['policy-api'], '/app/models/manifest.json'])
            run(['bash', str(ROOT / 'current/deploy/host/backup.sh')])
            values = dict(line.split('=', 1) for line in previous.decode().splitlines() if '=' in line and not line.startswith('#'))
            values.update(BACKEND_IMAGE=images['backend'], FRONTEND_IMAGE=images['frontend'], POLICY_IMAGE=images['policy-api'],
                          MODEL_CACHE_ID=hashlib.sha256(model_manifest).hexdigest()[:16])
            (ROOT / 'private/previous.env').write_bytes(previous)
            candidate = ROOT / 'private/production.env.new'
            candidate.write_text(''.join(key + '=' + value + '\n' for key, value in values.items()))
            candidate.replace(ENV)
            try:
                run(COMPOSE + ['config', '--quiet'])
                run(COMPOSE + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '180'], env=docker_environment)
                run(['bash', 'deploy/host/health.sh'])
                if schema() != previous_schema:
                    raise RuntimeError('Schema changed unexpectedly')
            except Exception:
                if schema() == previous_schema:
                    ENV.write_bytes(previous)
                    run(COMPOSE + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '180'], env=docker_environment)
                    print('Promotion failed; compatible previous images restored', flush=True)
                raise RuntimeError('Promotion failed; operator review required') from None
        evidence = {key: request[key] for key in ['version', 'source_commit', 'images', 'schema_version']}
        (ROOT / 'private/current-release.json').write_text(json.dumps(evidence, indent=2))
        print('Production promotion and HTTPS readiness passed', flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Production promotion failed; inspect private operator state without dumping credentials') from None
