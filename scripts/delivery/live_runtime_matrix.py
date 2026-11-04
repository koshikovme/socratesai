"""Real three-language verdict rehearsal, restricted to the authorized dedicated installation."""
import argparse
import json
import uuid
from pathlib import Path
from live_acceptance import LiveApi


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', required=True, type=Path)
    args = parser.parse_args()
    access = json.loads(args.access_file.read_text())
    api = LiveApi(access['base_url'])
    token = api.login(access['accounts']['probe'])['token']
    resources = access['resources']
    programs = json.loads((Path(__file__).resolve().parents[2] / 'deploy/demo/programs.json').read_text())
    failures = {
        'python3': ['print(0)', 'print(', 'raise RuntimeError()'],
        'java': [
            'public class Main { public static void main(String[] args) { System.out.println(0); }}',
            'public class Main { invalid syntax }',
            'public class Main { public static void main(String[] args) { throw new RuntimeException(); }}',
        ],
        'cpp17': ['#include <iostream>\nint main(){std::cout<<0;}', 'int main( { broken }',
                  '#include <cstdlib>\nint main(){std::abort();}'],
    }
    verdicts = ['WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR']
    results = []
    for language, program in programs.items():
        cases = [(program, 'ACCEPTED'), *zip(failures[language], verdicts, strict=True)]
        for source, verdict in cases:
            run = api.request('POST', f"/api/problems/{resources['problem_id']}/runs", {
                'problemVersionId': resources['version_id'], 'languageKey': language,
                'sourceCode': source, 'idempotencyKey': str(uuid.uuid4()),
            }, token, (202,))
            api.poll('/api/runs/' + run['id'], token, verdict)
            results.append({'language': language, 'verdict': verdict})
            print(json.dumps(results[-1]), flush=True)
    output = args.access_file.parent / 'production-runtime-matrix.json'
    output.write_text(json.dumps(results, indent=2))
    output.chmod(0o600)


if __name__ == '__main__':
    main()
