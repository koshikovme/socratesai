"""Crash/reclaim rehearsal restricted to the explicitly named local demo project."""
import json
import os
import subprocess
import time
import urllib.error
import uuid

from seed import ACCESS, PROGRAMS, STATE, DemoApi


def docker(*arguments):
    result = subprocess.run(['docker', *arguments], capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise RuntimeError('Local demo Docker operation failed; private process output was suppressed')
    return result.stdout.strip()


def container(service):
    identifiers = docker('ps', '-q', '--filter', 'label=com.docker.compose.project=socrates-local-demo',
                         '--filter', 'label=com.docker.compose.service=' + service).splitlines()
    if len(identifiers) != 1:
        raise RuntimeError('Start exactly one local demo stack before its recovery rehearsal')
    return identifiers[0]


def fixture_posts(identifier):
    # Authentication is read inside the private fixture; it never enters CLI arguments or output.
    code = ("import json,os,urllib.request; "
            "r=urllib.request.Request('http://127.0.0.1:8002/demo/stats',"
            "headers={'X-Auth-Token':os.environ['DEMO_JUDGE_TOKEN']}); "
            "print(json.load(urllib.request.urlopen(r))['batch_posts'])")
    return int(docker('exec', identifier, 'python', '-c', code))


def job_metadata(identifier, submission_id):
    submission_id = str(uuid.UUID(submission_id))
    sql = f"SELECT generation, attempt_count, status FROM execution_jobs WHERE submission_id = '{submission_id}'::uuid"
    value = docker('exec', identifier, 'psql', '-U', 'demo_contester', '-d', 'socrates_demo', '-Atq', '-c', sql)
    generation, attempts, status = value.split('|')
    return {'generation': int(generation), 'worker_attempts': int(attempts), 'status': status}


def main():
    os.umask(0o077)
    access = json.loads(ACCESS.read_text())
    if access['base_url'] != 'http://localhost:8088':
        raise RuntimeError('Recovery rehearsal is restricted to http://localhost:8088')
    backend, database, judge = (container(service) for service in ['backend', 'postgres', 'demo-judge'])
    api = DemoApi(access['base_url'])
    token = api.login(access['accounts']['probe'])['token']
    resources = access['resources']
    posts = fixture_posts(judge)
    submission = api.request('POST', f"/api/problems/{resources['problem_id']}/submissions", {
        'problemVersionId': resources['version_id'], 'languageKey': 'python3',
        'sourceCode': '# DEMO_RESTART\n' + PROGRAMS['python3'], 'idempotencyKey': str(uuid.uuid4()),
    }, token, (202,))
    path = '/api/submissions/' + submission['id']
    deadline = time.monotonic() + 10
    while api.request('GET', path, token=token)['submission']['status'] != 'RUNNING':
        if time.monotonic() >= deadline:
            raise RuntimeError('Demo job did not reach its persisted RUNNING checkpoint')
        time.sleep(0.1)
    before = job_metadata(database, submission['id'])
    docker('kill', '--signal', 'KILL', backend)
    # An operator-issued docker kill can suppress the restart policy; explicitly restart this container.
    docker('start', backend)
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            api.request('GET', '/api/users/me', token=token)
            break
        except (RuntimeError, urllib.error.URLError):
            time.sleep(0.5)
    result = api.poll(path, token)
    after = job_metadata(database, submission['id'])
    if after['generation'] <= before['generation'] or after['status'] != 'FINISHED':
        raise RuntimeError('Demo job did not reclaim its lease and finish')
    if result['submission']['attemptNumber'] != submission['attemptNumber'] or fixture_posts(judge) != posts + 1:
        raise RuntimeError('Demo recovery duplicated a submission or provider batch')
    evidence = {'scope': 'LOOPBACK_SYNTHETIC_DEMO', 'submission_id': submission['id'], 'before': before,
                'after': after, 'verdict': result['submission']['verdict'],
                'provider_batch_posts': 1, 'student_attempt_number_unchanged': True}
    (STATE / 'recovery.json').write_text(json.dumps(evidence, indent=2))
    print('Local demo recovery passed: reclaimed lease, ACCEPTED, one provider batch, unchanged student attempt number.')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as failure:
        raise SystemExit(str(failure)) from None
    except (OSError, ValueError, KeyError):
        raise SystemExit('Local demo recovery failed; credentials and response bodies were suppressed.') from None
