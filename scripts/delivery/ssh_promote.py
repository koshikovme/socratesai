"""Send the verified release manifest through a forced-command SSH deployment identity."""
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

stage = 'INPUT'


def main():
    global stage
    host = os.environ['PRODUCTION_HOST']
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]{0,252}', host):
        raise ValueError('Invalid deployment target')
    if os.environ.get('DEPLOY_HEALTH_ONLY') == 'true':
        command, payload = 'health', b''
    else:
        stage = 'MANIFEST'
        manifest = Path(os.environ['RELEASE_MANIFEST_DIRECTORY'])
        commit = (manifest / 'commit.txt').read_text().strip()
        if commit != os.environ['GITHUB_SHA']:
            raise ValueError('Release source does not match this workflow')
        references = (manifest / 'images.txt').read_text().splitlines()
        images = {reference.rsplit('/', 1)[-1].split('@', 1)[0]: reference for reference in references}
        schema_version = max(int(path.name.split('__', 1)[0][1:]) for path in Path('src/main/resources/db/migration').glob('V*__*.sql'))
        request = {'version': os.environ['GITHUB_REF_NAME'], 'source_commit': commit, 'images': images,
                   'schema_version': schema_version, 'registry_user': os.environ['GITHUB_ACTOR'],
                   'registry_password': os.environ['REGISTRY_TOKEN']}
        command, payload = 'promote', json.dumps(request).encode()
    with tempfile.TemporaryDirectory(prefix='socrates-ssh-') as folder:
        key, hosts = Path(folder) / 'key', Path(folder) / 'known_hosts'
        key.write_text(os.environ['PRODUCTION_SSH_KEY'].rstrip() + '\n'); key.chmod(0o600)
        hosts.write_text(os.environ['PRODUCTION_KNOWN_HOSTS'].rstrip() + '\n'); hosts.chmod(0o600)
        stage = 'SSH'
        result = subprocess.run(['ssh', '-i', str(key), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
            '-o', 'StrictHostKeyChecking=yes', '-o', 'UserKnownHostsFile=' + str(hosts),
            '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=20', 'root@' + host, command],
            input=payload, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=900)
        if result.returncode:
            error = result.stderr.decode(errors='replace')
            stage = next((label for text, label in [('error in libcrypto', 'SSH_KEY_INVALID'), ('invalid format', 'SSH_KEY_INVALID'),
                ('Host key verification failed', 'SSH_HOST_REJECTED'), ('Permission denied', 'SSH_AUTH_REJECTED'),
                ('Connection timed out', 'SSH_TIMEOUT')] if text in error), 'REMOTE_COMMAND')
            raise RuntimeError('Production promotion failed; private remote diagnostics suppressed')
    print('Production command passed: ' + command)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Production delivery failed at ' + stage + '; private diagnostics suppressed') from None
