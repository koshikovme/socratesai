"""Bounded synthetic 30-session/sample-run rehearsal, only on the authorized dedicated installation."""
import argparse
import concurrent.futures
import json
import math
import os
import secrets
import subprocess
import time
import uuid
from pathlib import Path
from live_acceptance import LiveApi


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', required=True, type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    access = json.loads(args.access_file.read_text())
    api = LiveApi(access['base_url'])
    private = args.access_file.parent
    accounts_file = private / 'production-load-accounts.json'
    if accounts_file.exists():
        accounts = json.loads(accounts_file.read_text())
    else:
        accounts = [{'email': f'acceptance-load-{number:02d}@socratesai.duckdns.org', 'password': secrets.token_urlsafe(24)} for number in range(30)]
        accounts_file.write_text(json.dumps(accounts))
    identities = []
    for account in accounts:
        try:
            identity = api.login(account)
        except RuntimeError:
            api.request('POST', '/api/auth/register', {**account, 'fullName': 'Acceptance load fixture', 'role': 'STUDENT'})
            identity = api.login(account)
        identities.append(identity)
    ready = private / 'production-load-ws-ready.json'
    ready.unlink(missing_ok=True)
    fixture = private / 'production-load-ws-input.json'
    fixture.write_text(json.dumps({'base_url': access['base_url'], **access['resources'],
                                  'identities': identities, 'ready_file': str(ready.resolve())}))
    with (private / 'production-load-ws.log').open('w') as output:
        sockets = subprocess.Popen(['node', str(root / 'scripts/delivery/live_capacity_ws.cjs'), str(fixture.resolve())],
                                   stdin=subprocess.PIPE, stdout=output, stderr=output)
        try:
            deadline = time.monotonic() + 35
            while not ready.exists():
                if sockets.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError('Thirty WSS sessions did not become ready')
                time.sleep(0.1)
            program = json.loads((root / 'deploy/demo/programs.json').read_text())['python3']

            def sample(identity):
                start = time.monotonic()
                run = api.request('POST', f"/api/problems/{access['resources']['problem_id']}/runs", {
                    'problemVersionId': access['resources']['version_id'], 'languageKey': 'python3',
                    'sourceCode': program, 'idempotencyKey': str(uuid.uuid4()),
                }, identity['token'], (202,))
                accepted_ms = (time.monotonic() - start) * 1000
                api.poll('/api/runs/' + run['id'], identity['token'])
                progress = api.request('GET', '/api/users/me/progress', token=identity['token'])
                assert progress['summary']['solvedTasks'] == 0
                return {'acceptance_ms': accepted_ms, 'completion_seconds': time.monotonic() - start}

            with concurrent.futures.ThreadPoolExecutor(max_workers=30) as executor:
                results = list(executor.map(sample, identities))
            acknowledgements = sorted(result['acceptance_ms'] for result in results)
            evidence = {**json.loads(ready.read_text()), 'acceptedSampleRuns': len(results), 'gradedAttemptsCreated': 0,
                        'post202P95Ms': round(acknowledgements[math.ceil(len(results) * 0.95) - 1], 1),
                        'maximumCompletionSeconds': round(max(result['completion_seconds'] for result in results), 1),
                        'scope': 'SYNTHETIC_SINGLE_PUBLIC_SAMPLE_NOT_A_CLASSROOM_SLA'}
            (private / 'production-capacity.json').write_text(json.dumps(evidence, indent=2))
            print(json.dumps(evidence))
        finally:
            if sockets.poll() is None:
                sockets.communicate(input=b'done\n', timeout=10)
            fixture.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
