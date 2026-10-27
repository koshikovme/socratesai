"""Bounded protocol fixture for LOCAL DEMO only. Never compiles/evals/executes submitted code."""
import base64
import hmac
import json
import os
import time
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

LANGUAGES = [
    {'id': 1, 'name': 'Synthetic Python demo (fixture-v1)'},
    {'id': 2, 'name': 'Synthetic Java demo (fixture-v1)'},
    {'id': 3, 'name': 'Synthetic C++ demo (fixture-v1)'},
]
KEYS = {1: 'python3', 2: 'java', 3: 'cpp17'}


class Fixture:
    def __init__(self, programs, clock=time.monotonic):
        self.programs = programs
        self.clock = clock
        self.results = {}
        self.batch_posts = 0

    def submit(self, submissions):
        if not isinstance(submissions, list) or not 1 <= len(submissions) <= 20:
            raise ValueError()
        self.results = {key: value for key, value in self.results.items() if value['expires'] > self.clock()}
        if len(self.results) + len(submissions) > 1024:
            raise ValueError()
        prepared = []
        for item in submissions:
            code = base64.b64decode(item['source_code'], validate=True).decode()
            stdin = base64.b64decode(item.get('stdin') or '', validate=True).decode()
            if len(code.encode()) > 80000 or len(stdin.encode()) > 65536 or item.get('enable_network') is not False:
                raise ValueError()
            key = KEYS[item['language_id']]
            slow = code.startswith('# DEMO_RESTART\n')
            candidate = code.removeprefix('# DEMO_RESTART\n').replace('\r\n', '\n').strip()
            if candidate == self.programs[key].strip():
                numbers = stdin.split()
                if len(numbers) != 2 or any(len(n) > 20 for n in numbers):
                    raise ValueError()
                stdout = str(sum(int(n) for n in numbers)) + '\n'
            else:
                stdout = 'Synthetic demo: use the documented example program\n'
            token = str(uuid.uuid4())
            prepared.append((token, {'ready': self.clock() + (12 if slow else 1), 'expires': self.clock() + 900,
                                     'stdout': base64.b64encode(stdout.encode()).decode()}))
        self.results.update(prepared)
        self.batch_posts += 1
        return [{'token': token} for token, _ in prepared]

    def poll(self, tokens):
        if not tokens or len(tokens) > 20 or any(token not in self.results for token in tokens):
            raise ValueError()
        return {'submissions': [dict(token=token, status={'id': 2 if self.results[token]['ready'] > self.clock() else 3},
                                     stdout=self.results[token]['stdout'], stderr=None, compile_output=None,
                                     time='0.001', memory=1024, exit_code=0) for token in tokens]}


def serve():
    if os.environ.get('SYNTHETIC_DEMO_ONLY') != 'true':
        raise SystemExit('Synthetic provider requires explicit local-demo mode')
    token = os.environ['DEMO_JUDGE_TOKEN']
    fixture = Fixture(json.loads(Path('/demo/programs.json').read_text()))

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format, *_args):
            pass  # No request paths/tokens/code in logs.

        def reply(self, status, body):
            encoded = json.dumps(body).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def authorized(self):
            return hmac.compare_digest(self.headers.get('X-Auth-Token', ''), token)

        def do_GET(self):
            path = urlsplit(self.path)
            if path.path == '/health':
                return self.reply(200, {'mode': 'SYNTHETIC_DEMO', 'executes_code': False})
            if not self.authorized():
                return self.reply(403, {'code': 'FORBIDDEN'})
            try:
                if path.path == '/languages':
                    return self.reply(200, LANGUAGES)
                if path.path == '/submissions/batch':
                    tokens = parse_qs(path.query).get('tokens', [''])[0].split(',')
                    return self.reply(200, fixture.poll(tokens))
                if path.path == '/demo/stats':
                    return self.reply(200, {'batch_posts': fixture.batch_posts, 'retained_results': len(fixture.results)})
            except (ValueError, KeyError, TypeError):
                return self.reply(400, {'code': 'INVALID_REQUEST'})
            self.reply(404, {'code': 'NOT_FOUND'})

        def do_POST(self):
            if not self.authorized():
                return self.reply(403, {'code': 'FORBIDDEN'})
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if urlsplit(self.path).path != '/submissions/batch' or not 1 <= size <= 3 * 1024 * 1024:
                    raise ValueError()
                data = json.loads(self.rfile.read(size))
                self.reply(201, fixture.submit(data['submissions']))
            except (ValueError, KeyError, TypeError, UnicodeError):
                self.reply(400, {'code': 'INVALID_REQUEST'})

    print('Synthetic local demo provider: student programs are never executed.', flush=True)
    HTTPServer(('0.0.0.0', 8002), Handler).serve_forever()


if __name__ == '__main__':
    serve()
