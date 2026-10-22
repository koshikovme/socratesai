"""Exercise each real .dockerignore with BuildKit and synthetic private files only."""
import pathlib
import shutil
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
for context in [root, root / 'socratesai-frontend', root / 'ml']:
    with tempfile.TemporaryDirectory() as tmp:
        fixture = pathlib.Path(tmp) / 'input'
        output = pathlib.Path(tmp) / 'output'
        fixture.mkdir()
        shutil.copy(context / '.dockerignore', fixture / '.dockerignore')
        # Valid inputs and forbidden files under both excluded and allowed parents.
        for name in ['Dockerfile', 'pom.xml', 'package.json', 'policy_api.py', 'src/main/java/Allowed.java', 'src/main.js']:
            target = fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('allowed fixture')
        for name in ['.env', '.env.prod', 'datasets/private.csv', 'experiments/private.json', 'ieee3rd2026/paper.tex',
                     'src/main/resources/.env', 'src/nested/.env.test', 'private.key', 'public/private.key', 'src/private.pem',
                     'src/main/resources/private.p12', 'node_modules/private.txt']:
            target = fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('PRIVATE_BUILD_CONTEXT_SENTINEL')
        subprocess.run(['docker', 'build', '--quiet', '--file', '-', '--output', f'type=local,dest={output}', str(fixture)],
                       input=b'FROM scratch\nCOPY . /\n', check=True, stdout=subprocess.DEVNULL)
        assert any(output.rglob('*')), 'Context allowlist excluded all application inputs'
        for file in output.rglob('*'):
            if file.is_file() and b'PRIVATE_BUILD_CONTEXT_SENTINEL' in file.read_bytes():
                raise RuntimeError('A build context included a forbidden fixture')
        print('Build context allowlist passed:', context.relative_to(root) if context != root else 'backend')
