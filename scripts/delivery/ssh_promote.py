"""Send the verified release manifest through a forced-command SSH deployment identity."""
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


def main():
    host = os.environ['PRODUCTION_HOST']
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]{0,252}', host):
        raise ValueError('Invalid deployment target')
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
    with tempfile.TemporaryDirectory(prefix='socrates-ssh-') as folder:
        key, hosts = Path(folder) / 'key', Path(folder) / 'known_hosts'
        key.write_text(os.environ['PRODUCTION_SSH_KEY'].rstrip() + '\n'); key.chmod(0o600)
        hosts.write_text(os.environ['PRODUCTION_KNOWN_HOSTS'].rstrip() + '\n'); hosts.chmod(0o600)
        result = subprocess.run(['ssh', '-i', str(key), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
            '-o', 'StrictHostKeyChecking=yes', '-o', 'UserKnownHostsFile=' + str(hosts),
            '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=20', 'root@' + host, 'promote'],
            input=json.dumps(request).encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=900)
        if result.returncode:
            raise RuntimeError('Production promotion failed; private remote diagnostics suppressed')
    print('Verified release promoted; HTTPS readiness passed')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Production delivery failed; inspect the operator runbook without dumping credentials') from None
