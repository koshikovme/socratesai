"""Fetch authenticated encrypted backups to the operator's protected workstation, never the executor."""
import argparse
import hashlib
import re
import shlex
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--identity-file', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    args.destination.mkdir(parents=True, exist_ok=True, mode=0o700)
    flags = ['-i', str(args.identity_file), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
             '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=15']
    listing = 'from pathlib import Path; print(chr(10).join(p.name for p in Path("/opt/socratesai/backups").glob("*.dump.cms")))'
    result = subprocess.run(['ssh', *flags, 'root@89.126.199.55', 'python3 -c ' + shlex.quote(listing)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=25, check=True)
    count = 0
    for name in result.stdout.decode().splitlines():
        if not re.fullmatch(r'socrates-\d{8}T\d{6}Z\.dump\.cms', name):
            raise ValueError('Invalid backup identifier')
        target = args.destination / name
        for suffix in ['', '.sha256']:
            destination = args.destination / (name + suffix)
            if not destination.exists():
                temporary = destination.with_name(destination.name + '.partial')
                subprocess.run(['scp', *flags, 'root@89.126.199.55:/opt/socratesai/backups/' + name + suffix, str(temporary)],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=240, check=True)
                temporary.chmod(0o600); temporary.replace(destination)
        expected = (args.destination / (name + '.sha256')).read_text().split()[0]
        with target.open('rb') as source:
            actual = hashlib.file_digest(source, 'sha256').hexdigest()
        if not re.fullmatch(r'[a-f0-9]{64}', expected) or actual != expected:
            raise ValueError('Backup checksum mismatch')
        count += 1
    print(f'Encrypted off-host backups verified: {count}')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Encrypted backup fetch failed; inspect operator connectivity without dumping credentials') from None
