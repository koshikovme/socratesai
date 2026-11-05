"""Explicitly authorized synthetic acceptance on the dedicated SocratesAI HTTPS installation.

Credentials and provider tickets stay in the ignored operator file. This script never executes
programs locally: every program goes through the application's isolated CodeExecutionPort.
"""
import argparse
import json
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


class LiveApi:
    def __init__(self, base):
        if base != 'https://socratesai.duckdns.org':
            raise ValueError('Acceptance is restricted to the explicitly authorized dedicated installation')
        self.base = base

    def request(self, method, path, body=None, token=None, expected=(200,)):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=json.dumps(body).encode() if body is not None else None)
        try:
            response = urllib.request.urlopen(request, timeout=25)
        except urllib.error.HTTPError as failure:
            response = failure
        if response.status not in expected:
            raise RuntimeError(f'Acceptance HTTP failure: {method} {path} ({response.status})') from None
        data = response.read()
        return json.loads(data) if data else None

    def login(self, account):
        return self.request('POST', '/api/auth/login', account)

    def poll(self, path, token, expected_verdict='ACCEPTED'):
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            value = self.request('GET', path, token=token)
            summary = value.get('submission', value)
            if summary['status'] in ['FINISHED', 'SYSTEM_ERROR'] and not value.get('retryScheduled'):
                if summary.get('verdict') != expected_verdict:
                    raise RuntimeError(f"Acceptance verdict mismatch: expected {expected_verdict}, received {summary.get('verdict')}")
                return value
            time.sleep(0.5)
        raise RuntimeError('Acceptance polling deadline exceeded')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', required=True, type=Path)
    parser.add_argument('--run-label', default='', help='Stable release label for fresh, repeatable idempotency keys')
    args = parser.parse_args()
    access = json.loads(args.access_file.read_text())
    api = LiveApi(access['base_url'])
    accounts = access['accounts']
    identities = {'admin': api.login(accounts['admin'])}
    for role in ['teacher', 'student', 'probe']:
        try:
            identities[role] = api.login(accounts[role])
        except RuntimeError:
            api.request('POST', '/api/auth/register', {**accounts[role], 'fullName': 'Acceptance ' + role, 'role': 'STUDENT'})
            identities[role] = api.login(accounts[role])
    teacher = identities['teacher']
    if teacher['role'] != 'TEACHER':
        api.request('PATCH', f"/api/admin/users/{teacher['userId']}/role",
                    {'expectedRole': 'STUDENT', 'role': 'TEACHER', 'reason': 'Authorized dedicated-host acceptance'}, identities['admin']['token'])
        teacher = api.login(accounts['teacher'])
    programs = json.loads((Path(__file__).resolve().parents[2] / 'deploy/demo/programs.json').read_text())
    resources = access.get('resources')
    if not resources and not access.get('pending_problem'):
        problem = api.request('POST', '/api/teacher/problems', {
            'lockVersion': 0, 'title': 'Сумма двух чисел', 'topic': 'Ввод и вывод', 'difficulty': 'EASY',
            'statement': 'Прочитайте два целых числа и выведите их сумму. Решение читает стандартный ввод и пишет в стандартный вывод.',
            'inputFormat': 'Два целых числа через пробел.', 'outputFormat': 'Их сумма и перевод строки.',
            'constraintsText': 'Числа от −1000 до 1000.', 'comparisonMode': 'IGNORE_TRAILING_LINE_ENDINGS', 'visibility': 'PUBLIC',
            'languages': [{'languageKey': key, 'starterCode': '', 'timeLimitMs': 2000, 'wallTimeLimitMs': 5000,
                           'memoryLimitKb': 262144, 'outputLimitBytes': 65536} for key in programs],
            'tests': [{'position': 0, 'inputData': '2 3\n', 'expectedOutput': '5\n', 'hidden': False},
                      {'position': 1, 'inputData': '173 251\n', 'expectedOutput': '424\n', 'hidden': True}],
        }, teacher['token'], (201,))
        access['pending_problem'] = problem
        args.access_file.write_text(json.dumps(access, indent=2))
    if not resources:
        problem = access['pending_problem']
        proof = api.request('POST', f"/api/teacher/problems/{problem['id']}/verify", {
            'lockVersion': problem['lockVersion'], 'solutions': [{'languageKey': key, 'sourceCode': source} for key, source in programs.items()],
        }, teacher['token'], (202,))
        access['verification_id'] = proof['id']; args.access_file.write_text(json.dumps(access, indent=2))
        api.poll(f"/api/teacher/problems/{problem['id']}/verifications/{proof['id']}", teacher['token'])
        problem = api.request('POST', f"/api/teacher/problems/{problem['id']}/publish",
                              {'lockVersion': problem['lockVersion'], 'verificationId': proof['id']}, teacher['token'])
        collection = api.request('POST', '/api/teacher/collections', {
            'lockVersion': 0, 'title': 'Основы программирования', 'description': 'Первые задачи со стандартным вводом и выводом.',
            'visibility': 'PUBLIC', 'problems': [{'problemId': problem['id'], 'problemVersionId': problem['publishedVersionId']}],
        }, teacher['token'], (201,))
        collection = api.request('POST', f"/api/teacher/collections/{collection['id']}/publish", {'lockVersion': collection['lockVersion']}, teacher['token'])
        resources = {'problem_id': problem['id'], 'version_id': problem['publishedVersionId'], 'collection_id': collection['id']}
        access['resources'] = resources; args.access_file.write_text(json.dumps(access, indent=2))
    probe = identities['probe']['token']
    results = []
    for language, source in programs.items():
        namespace = f"socrates-live/{resources['version_id']}/{language}"
        if args.run_label:
            namespace += '/' + args.run_label
        body = {'problemVersionId': resources['version_id'], 'languageKey': language, 'sourceCode': source,
                'idempotencyKey': str(uuid.uuid5(uuid.NAMESPACE_URL, namespace + '/run'))}
        run = api.request('POST', f"/api/problems/{resources['problem_id']}/runs", body, probe, (202,))
        api.poll('/api/runs/' + run['id'], probe)
        body['idempotencyKey'] = str(uuid.uuid5(uuid.NAMESPACE_URL, namespace + '/submit'))
        body['collectionId'] = resources['collection_id']
        submission = api.request('POST', f"/api/problems/{resources['problem_id']}/submissions", body, probe, (202,))
        same = api.request('POST', f"/api/problems/{resources['problem_id']}/submissions", body, probe, (202,))
        assert same['id'] == submission['id']
        status = api.poll('/api/submissions/' + submission['id'], probe)
        assert all(not ({'stdout', 'stderr', 'compileOutput', 'sourceCode', 'inputData', 'expectedOutput'} & set(case)) for case in status['cases'])
        results.append({'language': language, 'run': 'ACCEPTED', 'submission': 'ACCEPTED', 'idempotency': True})
    progress = api.request('GET', '/api/users/me/progress', token=probe)
    assert progress['progressSource'] == 'JUDGED_SUBMISSION'
    assert any(task['taskId'] == resources['problem_id'] and task['status'] == 'SOLVED' for task in progress['tasks'])
    collection_progress = api.request('GET', f"/api/collections/{resources['collection_id']}/progress", token=probe)
    assert any(item['problemId'] == resources['problem_id'] and item['problemVersionId'] == resources['version_id']
               and item['solved'] is True for item in collection_progress['problems'])
    public = api.request('GET', f"/api/problems/{resources['problem_id']}")
    assert '173 251' not in json.dumps(public) and 'referenceSolution' not in json.dumps(public)
    for token in [None, identities['student']['token']]:
        api.request('GET', f"/api/teacher/problems/{resources['problem_id']}", token=token, expected=(401, 403))
    api.request('GET', '/api/submissions/' + submission['id'], token=identities['student']['token'], expected=(403, 404))
    print(json.dumps({'journeys': results, 'progress': 'SOLVED', 'private_projections': 'passed', 'ownership': 'passed'}))


if __name__ == '__main__':
    main()
