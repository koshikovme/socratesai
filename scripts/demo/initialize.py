"""Create local-only credentials once. Secrets stay in ignored owner-only files."""
import base64
import json
import os
import secrets
from pathlib import Path

root = Path(__file__).resolve().parents[2]
state = root / '.delivery/demo'
state.mkdir(parents=True, exist_ok=True, mode=0o700)
state.chmod(0o700)
os.umask(0o077)
access_path = state / 'access.json'
if not access_path.exists():
    accounts = {key: {'email': f'{key}@socrates.demo.invalid', 'password': secrets.token_hex(12)}
                for key in ['admin', 'teacher', 'student', 'probe']}
    values = {
        'BACKEND_IMAGE': 'socratesai/backend:delivery-test', 'FRONTEND_IMAGE': 'socratesai/frontend:delivery-test',
        'POLICY_IMAGE': 'socratesai/policy-api:delivery-test', 'APP_DOMAIN': 'localhost',
        'APP_PUBLIC_ORIGIN': 'http://localhost:8088', 'TLS_DIRECTORY': str(state),
        'POSTGRES_DB': 'socrates_demo', 'POSTGRES_USER': 'demo_contester', 'POSTGRES_PASSWORD': secrets.token_hex(32),
        'POSTGRES_ADMIN_PASSWORD': secrets.token_hex(32), 'APP_JWT_SECRET': base64.b64encode(secrets.token_bytes(64)).decode(),
        'APP_RUNTIME_CONFIG_FILE': str(root / 'deploy/demo/runtime-catalog.yaml'),
        'APP_ADMIN_BOOTSTRAP_ENABLED': 'true', 'APP_ADMIN_BOOTSTRAP_EMAIL': accounts['admin']['email'],
        'APP_ADMIN_BOOTSTRAP_PASSWORD': accounts['admin']['password'], 'APP_ADMIN_BOOTSTRAP_FULL_NAME': 'Local demo administrator',
        'APP_JUDGE0_API_KEY': secrets.token_hex(32), 'APP_GEMINI_ENABLED': 'false', 'APP_OPENAI_ENABLED': 'false',
        'APP_POLICY_ML_ENABLED': 'true', 'APP_JUDGING_ENABLED': 'true', 'APP_JUDGE0_ENABLED': 'true',
    }
    # Credentials are injected through Compose environment; no secrets in image layers or arguments.
    with (state / 'compose.env').open('x') as stream:
        stream.write(''.join(f'{key}={json.dumps(value)}\n' for key, value in values.items()))
    with access_path.open('x') as stream:
        json.dump({'base_url': values['APP_PUBLIC_ORIGIN'], 'accounts': accounts, 'resources': {}}, stream, indent=2)
for path in [access_path, state / 'compose.env']:
    path.chmod(0o600)
