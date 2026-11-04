"""Crash/reclaim rehearsal on the two explicitly authorized, dedicated production hosts.

Run during an operator maintenance window with synthetic identities only.
"""
import argparse
import json
import subprocess
import time
import urllib.error
import uuid
from pathlib import Path
from live_acceptance import LiveApi

HOSTS = {'main': '89.126.199.55', 'judge': '89.126.199.58'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', required=True, type=Path)
    parser.add_argument('--identity-file', required=True, type=Path)
    args = parser.parse_args()

    def remote(role, command):
        result = subprocess.run(['ssh', '-i', str(args.identity_file), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
            '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', 'root@' + HOSTS[role], command],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=35)
        if result.returncode:
            raise RuntimeError('Dedicated-host operation failed; private diagnostics suppressed')
        return result.stdout.decode().strip()

    def provider_count():
        return int(remote('judge', 'docker exec socrates-judge-db-1 psql -U judge_runtime -d judge0 -Atqc "SELECT count(*) FROM submissions"'))

    def metadata(identifier):
        identifier = str(uuid.UUID(identifier))
        sql = ("SELECT j.generation,j.attempt_count,j.status,count(r.provider_token) FROM execution_jobs j "
               "LEFT JOIN execution_case_results r ON r.job_id=j.id WHERE j.submission_id='" + identifier + "'::uuid GROUP BY j.id")
        value = remote('main', 'docker exec socrates-production-postgres-1 psql -U socrates -d socratesdb -Atqc "' + sql + '"')
        generation, attempts, status, tickets = value.split('|')
        return {'generation': int(generation), 'worker_attempts': int(attempts), 'status': status, 'persisted_tickets': int(tickets)}

    access = json.loads(args.access_file.read_text())
    api = LiveApi(access['base_url'])
    token = api.login(access['accounts']['probe'])['token']
    resources = access['resources']
    programs = json.loads((Path(__file__).resolve().parents[2] / 'deploy/demo/programs.json').read_text())
    count_before = provider_count()
    submission = api.request('POST', f"/api/problems/{resources['problem_id']}/submissions", {
        'problemVersionId': resources['version_id'], 'languageKey': 'python3',
        'sourceCode': 'import time\ntime.sleep(1.5)\n' + programs['python3'], 'idempotencyKey': str(uuid.uuid4()),
    }, token, (202,))
    deadline = time.monotonic() + 30
    while True:
        before = metadata(submission['id'])
        if before['status'] == 'RUNNING' and before['persisted_tickets'] == 2:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError('Synthetic job did not reach its durable ticket checkpoint')
        time.sleep(0.1)
    remote('main', 'docker kill --signal KILL socrates-production-backend-1 >/dev/null; docker start socrates-production-backend-1 >/dev/null')
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        try:
            api.request('GET', '/api/users/me', token=token)
            break
        except (RuntimeError, urllib.error.URLError):
            time.sleep(0.5)
    result = api.poll('/api/submissions/' + submission['id'], token)
    after = metadata(submission['id'])
    if after['generation'] <= before['generation'] or after['status'] != 'FINISHED':
        raise RuntimeError('Job was not reclaimed successfully')
    if result['submission']['attemptNumber'] != submission['attemptNumber'] or provider_count() != count_before + 2:
        raise RuntimeError('Recovery duplicated a student attempt or provider execution')
    evidence = {'scope': 'AUTHORIZED_DEDICATED_PRODUCTION', 'before': before, 'after': after,
                'verdict': result['submission']['verdict'], 'provider_executions': 2, 'student_attempt_unchanged': True}
    (args.access_file.parent / 'production-recovery.json').write_text(json.dumps(evidence, indent=2))
    print(json.dumps(evidence))


if __name__ == '__main__':
    main()
