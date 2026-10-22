"""HTTP/TLS checks with ephemeral credentials kept in memory. No response/body logging."""
import json
import re
import os
import ssl
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

values = dict(line.split('=', 1) for line in (Path(os.environ['DELIVERY_TMP']) / 'compose.env').read_text().splitlines())
base = values['APP_PUBLIC_ORIGIN']
context = ssl.create_default_context(cafile=values['TLS_DIRECTORY'] + '/fullchain.pem')


def request(path, body=None, token=None, expected=200, origin=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if origin:
        headers['Origin'] = origin
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    try:
        response = urllib.request.urlopen(req, context=context, timeout=15)
    except urllib.error.HTTPError as failure:
        response = failure
    if response.status != expected:
        raise RuntimeError('Container smoke request failed: ' + path) from None
    raw = response.read()
    return json.loads(raw) if response.headers.get_content_type() == 'application/json' else raw


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


opener = urllib.request.build_opener(NoRedirect)
try:
    opener.open('http://localhost:' + values['EDGE_HTTP_PORT'] + '/dashboard', timeout=5)
    raise RuntimeError('HTTPS redirect is missing')
except urllib.error.HTTPError as redirect:
    assert redirect.code == 308 and redirect.headers['Location'] == base + '/dashboard'
html = request('/problems/123?version=456')
assert b'<div id="app">' in html
script = re.search(rb'<script[^>]+src="([^"]+)"', html)
assert script and request(script.group(1).decode())

request('/api/auth/register', {'email': 'escalation@example.test', 'fullName': 'Fixture', 'password': values['POSTGRES_PASSWORD'], 'role': 'ADMIN'}, expected=400)
request('/api/auth/register', {'email': 'student@example.test', 'fullName': 'Fixture student', 'password': values['POSTGRES_PASSWORD'], 'role': 'STUDENT'})
student = request('/api/auth/login', {'email': 'student@example.test', 'password': values['POSTGRES_PASSWORD']})
assert student['role'] == 'STUDENT'
request('/api/users/me', token=student['token'])
request('/api/teacher/problems', token=student['token'], expected=403)
request('/ws/info', origin=base)
request('/ws/info', origin='https://foreign.example.test', expected=403)
admin = request('/api/auth/login', {'email': values['APP_ADMIN_BOOTSTRAP_EMAIL'], 'password': values['APP_ADMIN_BOOTSTRAP_PASSWORD']})
assert admin['role'] == 'ADMIN'
request('/api/teacher/problems', token=admin['token'])
# Credentials travel in the child environment, never CLI arguments or printed output.
environment = {**os.environ, 'SMOKE_WS_ORIGIN': base, 'SMOKE_WS_TOKEN': student['token'], 'NODE_EXTRA_CA_CERTS': values['TLS_DIRECTORY'] + '/fullchain.pem'}
subprocess.run(['node', str(Path(os.environ['DELIVERY_ROOT']) / 'scripts/delivery/smoke_ws.mjs')], env=environment, check=True, timeout=20)
print('HTTPS SPA, native auth, role denials, exact-origin SockJS and authenticated STOMP passed.')
