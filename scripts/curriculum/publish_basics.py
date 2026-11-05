"""Publish the authorized beginner pack through normal staff APIs; never execute programs here."""
import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'delivery'))
from live_acceptance import LiveApi


def read(path):
    return json.loads(path.read_text())


def require(condition):
    if not condition:
        raise ValueError('Publication input/state mismatch; reconcile protected phase and IDs')


def write(path, data):
    temporary = path.with_suffix('.partial')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    temporary.chmod(0o600)
    temporary.replace(path)


def check_pack(content, starters, validation):
    require(content['version'] == 1 and content['languages'] == ['java', 'cpp17'])
    require(len(content['problems']) == 10 and len({problem['key'] for problem in content['problems']}) == 10)
    for problem in content['problems']:
        require(problem['samples'] and problem['title'] and problem['statement'])
        private = validation[problem['key']]
        require(private['hiddenTests'])
        for test in problem['samples'] + private['hiddenTests']:
            require(isinstance(test['inputData'], str) and isinstance(test['expectedOutput'], str))
            require(len(test['inputData'].encode()) <= 65536 and len(test['expectedOutput'].encode()) <= 65536)
        require(len(problem['samples']) + len(private['hiddenTests']) <= 100)
        for language in content['languages']:
            require(starters[problem['key']][language].strip())
            source = private['referenceSolutions'][language]
            require(source.strip() and len(source.encode()) <= 20000)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', type=Path)
    parser.add_argument('--starters-file', type=Path, required=True)
    parser.add_argument('--validation-file', type=Path, required=True, help='Protected external hidden cases and reference programs')
    parser.add_argument('--state-file', type=Path, required=True)
    parser.add_argument('--owner-account', default='teacher')
    parser.add_argument('--check', action='store_true', help='Validate files without making any network request')
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    content_path = root / 'content/basics/problems.json'
    content, starters, validation = read(content_path), read(args.starters_file), read(args.validation_file)
    check_pack(content, starters, validation)
    if args.check:
        print('Validated 20 starter/reference pairs and ten problem definitions; no network or execution')
        return
    if args.access_file is None:
        raise ValueError('Private access file required')
    access = read(args.access_file)
    api = LiveApi(access['base_url'])
    identity = api.login(access['accounts'][args.owner_account])
    require(identity['role'] in ('TEACHER', 'ADMIN'))
    token = identity['token']
    digest = hashlib.sha256(content_path.read_bytes() + args.starters_file.read_bytes() + args.validation_file.read_bytes()).hexdigest()
    state = read(args.state_file) if args.state_file.exists() else {'ownerId': identity['userId'], 'packHash': digest, 'problems': {}, 'collections': {}}
    require(state['ownerId'] == identity['userId'] and state['packHash'] == digest)
    args.state_file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    write(args.state_file, state)
    labels = {'java': 'Java', 'cpp17': 'C++'}

    for language in content['languages']:
        for index, problem in enumerate(content['problems'], 1):
            key = language + ':' + problem['key']
            saved = state['problems'].setdefault(key, {})
            if not saved.get('id'):
                # An unknown POST outcome is never blindly repeated on a subsequent run.
                require(not saved.get('phase'))
                saved['phase'] = 'creating-unknown'; write(args.state_file, state)
                draft = {
                    'lockVersion': 0, 'title': f"{labels[language]} · {index:02d}. {problem['title']}",
                    'topic': problem['topic'], 'difficulty': 'EASY',
                    'statement': problem['statement'] + '\n\nРеализуйте функцию `' + problem['contract']['functionName']
                        + '`. Имена и типы параметров указаны в начальном коде. Код чтения ввода и вывода ответа уже подготовлен.',
                    'inputFormat': problem['inputFormat'], 'outputFormat': problem['outputFormat'], 'constraintsText': problem['constraintsText'],
                    'comparisonMode': 'IGNORE_TRAILING_LINE_ENDINGS', 'visibility': 'PUBLIC',
                    'languages': [{'languageKey': language, 'starterCode': starters[problem['key']][language], 'timeLimitMs': 2000,
                                   'wallTimeLimitMs': 5000, 'memoryLimitKb': 262144, 'outputLimitBytes': 65536}],
                    'tests': [{**test, 'position': position, 'hidden': False} for position, test in enumerate(problem['samples'])]
                        + [{**test, 'position': len(problem['samples']) + position, 'hidden': True}
                           for position, test in enumerate(validation[problem['key']]['hiddenTests'])],
                }
                created = api.request('POST', '/api/teacher/problems', draft, token, (201,))
                saved.update(id=created['id'], versionId=created['version']['id'], phase='draft')
                write(args.state_file, state)
            current = api.request('GET', f"/api/teacher/problems/{saved['id']}", token=token)
            if current['publishedVersionId'] == saved['versionId']:
                saved['phase'] = 'published'; write(args.state_file, state)
                continue
            require(current['version']['id'] == saved['versionId'] and current['version']['status'] == 'DRAFT')
            if not saved.get('verificationId'):
                require(saved['phase'] == 'draft')
                saved['phase'] = 'verifying-unknown'; write(args.state_file, state)
                verification = api.request('POST', f"/api/teacher/problems/{saved['id']}/verify", {
                    'lockVersion': current['lockVersion'], 'solutions': [{'languageKey': language, 'sourceCode': validation[problem['key']]['referenceSolutions'][language]}],
                }, token, (202,))
                saved.update(verificationId=verification['id'], phase='verifying'); write(args.state_file, state)
            proof = api.poll(f"/api/teacher/problems/{saved['id']}/verifications/{saved['verificationId']}", token)
            require(proof['testsPassed'] == proof['testsTotal'] and proof['testsTotal'] > 0)
            saved['phase'] = 'publishing-unknown'; write(args.state_file, state)
            published = api.request('POST', f"/api/teacher/problems/{saved['id']}/publish", {
                'lockVersion': current['lockVersion'], 'verificationId': saved['verificationId'],
            }, token)
            require(published['publishedVersionId'] == saved['versionId'])
            saved['phase'] = 'published'; write(args.state_file, state)
            print(json.dumps({'problemKey': key, 'problemId': saved['id'], 'versionId': saved['versionId'], 'referenceVerdict': 'ACCEPTED'}), flush=True)

        collection = state['collections'].setdefault(language, {})
        members = [{'problemId': state['problems'][language + ':' + problem['key']]['id'],
                    'problemVersionId': state['problems'][language + ':' + problem['key']]['versionId']} for problem in content['problems']]
        if not collection.get('id'):
            require(not collection.get('phase'))
            collection['phase'] = 'creating-unknown'; write(args.state_file, state)
            created = api.request('POST', '/api/teacher/collections', {
                'lockVersion': 0, 'title': labels[language] + ': основы программирования',
                'description': '10 задач для начинающих: арифметика, условия, циклы, массивы и строки.\n\n'
                    + 'Пишите логику в функции с именованными параметрами. Чтение ввода и вывод ответа уже есть в шаблоне. '
                    + 'Только принятая зачётная отправка засчитывается как решение; подсказки и запуск примеров помогают подготовиться.',
                'visibility': 'PUBLIC', 'problems': members,
            }, token, (201,))
            collection.update(id=created['id'], phase='draft'); write(args.state_file, state)
        current = api.request('GET', f"/api/teacher/collections/{collection['id']}", token=token)
        require([(member['problemId'], member['problemVersionId']) for member in current['problems']] == [(member['problemId'], member['problemVersionId']) for member in members])
        if current['status'] != 'PUBLISHED':
            api.request('POST', f"/api/teacher/collections/{collection['id']}/publish", {'lockVersion': current['lockVersion']}, token)
        collection['phase'] = 'published'; write(args.state_file, state)
        print(json.dumps({'language': language, 'collectionId': collection['id'], 'publishedProblems': len(members)}), flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Beginner publication stopped; inspect protected phase/IDs, never retry an uncertain POST blindly. Private diagnostics suppressed.') from None
