"""Seed native APIs, staff reference verification and a probe submission in the synthetic demo."""
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / '.delivery/demo'
ACCESS = STATE / 'access.json'
PROGRAMS = json.loads((ROOT / 'deploy/demo/programs.json').read_text())


class DemoApi:
    def __init__(self, base):
        if base != 'http://localhost:8088':
            raise RuntimeError('Demo API seeding is restricted to http://localhost:8088')
        self.base = base

    def request(self, method, path, body=None, token=None, expected=(200,)):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=json.dumps(body).encode() if body is not None else None)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as failure:
            response = failure
        if response.status not in expected:
            raise RuntimeError(f'Demo API check failed: {method} {path} (HTTP {response.status})') from None
        return json.loads(response.read())

    def login(self, account):
        return self.request('POST', '/api/auth/login', account)

    def poll(self, path, token):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            value = self.request('GET', path, token=token)
            summary = value.get('submission', value)
            if summary['status'] in ['FINISHED', 'SYSTEM_ERROR'] and not value.get('retryScheduled'):
                if summary.get('verdict') != 'ACCEPTED':
                    raise RuntimeError('Synthetic demo verification did not accept its documented fixture')
                return value
            time.sleep(0.4)
        raise RuntimeError('Synthetic demo verification timed out')


def main():
    os.umask(0o077)
    access = json.loads(ACCESS.read_text())
    api = DemoApi(access['base_url'])
    accounts = access['accounts']
    admin = api.login(accounts['admin'])
    identities = {'admin': admin}
    for key in ['teacher', 'student', 'probe']:
        try:
            identity = api.login(accounts[key])
        except RuntimeError:
            api.request('POST', '/api/auth/register', {**accounts[key], 'fullName': 'Local demo ' + key, 'role': 'STUDENT'})
            identity = api.login(accounts[key])
        identities[key] = identity
    teacher = identities['teacher']
    if teacher['role'] != 'TEACHER':
        api.request('PATCH', f"/api/admin/users/{teacher['userId']}/role",
                    {'expectedRole': 'STUDENT', 'role': 'TEACHER', 'reason': 'Isolated synthetic local demonstration'}, admin['token'])
        teacher = api.login(accounts['teacher'])
    resources = access.get('resources', {})
    if not resources:
        problem = api.request('POST', '/api/teacher/problems', {
            'lockVersion': 0, 'title': 'ДЕМО · Сумма двух чисел', 'topic': 'Local synthetic demo', 'difficulty': 'EASY',
            'statement': 'Прочитайте два целых числа и выведите сумму.\n\nЛОКАЛЬНОЕ ДЕМО: проверка имитируется, код не выполняется. '
                         'Здесь принимаются только точные шаблоны из локального файла access.md. '
                         'Изменённый шаблон демонстрирует WRONG_ANSWER. Для реальной проверки нужен отдельный Judge0.',
            'inputFormat': 'Два целых числа через пробел.', 'outputFormat': 'Их сумма и перевод строки.',
            'constraintsText': 'Только синтетические учебные данные.', 'comparisonMode': 'IGNORE_TRAILING_LINE_ENDINGS',
            'visibility': 'PUBLIC',
            'languages': [{'languageKey': key, 'starterCode': '# Paste the documented demo program\n' if key == 'python3' else '',
                           'timeLimitMs': 2000, 'wallTimeLimitMs': 5000, 'memoryLimitKb': 262144, 'outputLimitBytes': 65536}
                          for key in PROGRAMS],
            'tests': [{'position': 0, 'inputData': '2 3\n', 'expectedOutput': '5\n', 'hidden': False},
                      {'position': 1, 'inputData': '17 25\n', 'expectedOutput': '42\n', 'hidden': True}],
        }, teacher['token'], (201,))
        proof = api.request('POST', f"/api/teacher/problems/{problem['id']}/verify", {
            'lockVersion': problem['lockVersion'],
            'solutions': [{'languageKey': key, 'sourceCode': source} for key, source in PROGRAMS.items()],
        }, teacher['token'], (202,))
        api.poll(f"/api/teacher/problems/{problem['id']}/verifications/{proof['id']}", teacher['token'])
        problem = api.request('POST', f"/api/teacher/problems/{problem['id']}/publish",
                              {'lockVersion': problem['lockVersion'], 'verificationId': proof['id']}, teacher['token'])
        collection = api.request('POST', '/api/teacher/collections', {
            'lockVersion': 0, 'title': 'ДЕМО · Первые задачи', 'description': 'Локальная демонстрация с имитацией проверки.',
            'visibility': 'PUBLIC', 'problems': [{'problemId': problem['id'], 'problemVersionId': problem['publishedVersionId']}],
        }, teacher['token'], (201,))
        collection = api.request('POST', f"/api/teacher/collections/{collection['id']}/publish",
                                 {'lockVersion': collection['lockVersion']}, teacher['token'])
        resources = {'problem_id': problem['id'], 'version_id': problem['publishedVersionId'], 'collection_id': collection['id']}
        access['resources'] = resources
        ACCESS.write_text(json.dumps(access, indent=2))
    # Probe has its own account, leaving the visible student's initial progress unsolved.
    probe = identities['probe']['token']
    for key, source in PROGRAMS.items():
        body = {'problemVersionId': resources['version_id'], 'languageKey': key, 'sourceCode': source,
                'idempotencyKey': str(uuid.uuid5(uuid.NAMESPACE_URL, f"socrates-demo/{resources['version_id']}/{key}/sample"))}
        run = api.request('POST', f"/api/problems/{resources['problem_id']}/runs", body, probe, (202,))
        api.poll('/api/runs/' + run['id'], probe)
        body['idempotencyKey'] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"socrates-demo/{resources['version_id']}/{key}/submit"))
        body['collectionId'] = resources['collection_id']
        submission = api.request('POST', f"/api/problems/{resources['problem_id']}/submissions", body, probe, (202,))
        status = api.poll('/api/submissions/' + submission['id'], probe)
        assert all(not ({'stdout', 'stderr', 'compileOutput', 'sourceCode'} & set(case)) for case in status['cases'])
    progress = api.request('GET', '/api/users/me/progress', token=probe)
    assert progress['summary']['solvedTasks'] == 1
    lines = ['# Локальная демонстрация SocratesAI', '', 'Адрес: ' + access['base_url'], '',
             '**Проверка решений синтетическая: программы не выполняются. Не вводите реальные приватные данные.**', '',
             '## Учебные аккаунты (только этот локальный стенд)', '']
    for key in ['admin', 'teacher', 'student']:
        lines.extend([f'### {key}', '', f"Логин: `{accounts[key]['email']}`", f"Пароль: `{accounts[key]['password']}`", ''])
    lines.extend(['## Шаблоны для имитации ACCEPTED', '', 'Вставьте целиком один шаблон в Monaco, затем Run samples и Submit.', ''])
    for key, source in PROGRAMS.items():
        lines.extend(['### ' + key, '', '```' + key, source.rstrip(), '```', ''])
    (STATE / 'access.md').write_text('\n'.join(lines))
    (STATE / 'access.md').chmod(0o600)
    print('Native demo accounts, teacher publication, collection and three-language probe checks passed (SYNTHETIC judging).')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as failure:
        raise SystemExit(str(failure)) from None
    except (OSError, ValueError, AssertionError):
        raise SystemExit('Demo initialization/verification failed. No credentials or response bodies were printed.') from None
