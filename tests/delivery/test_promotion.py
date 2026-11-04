"""Promotion recovery tests use temporary files and a fake process boundary, never hosts/databases."""
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

source = Path(__file__).resolve().parents[2] / 'deploy/host/promote.py'
spec = importlib.util.spec_from_file_location('production_promotion', source)
promotion = importlib.util.module_from_spec(spec)
spec.loader.exec_module(promotion)


class PromotionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        (self.root / 'private').mkdir()
        (self.root / 'current').mkdir()
        self.env = self.root / 'private/production.env'
        self.previous = b'BACKEND_IMAGE=previous-backend\nFRONTEND_IMAGE=previous-frontend\nPOLICY_IMAGE=previous-policy\nPOSTGRES_PASSWORD=fixture-password\n'
        self.env.write_bytes(self.previous)
        self.request = {'version': 'v0.9.0', 'source_commit': 'a' * 40, 'schema_version': 13,
            'registry_user': 'fixture-operator', 'registry_password': 'PRIVATE_REGISTRY_FIXTURE',
            'images': {name: 'ghcr.io/koshikovme/socratesai/' + name + '@sha256:' + 'b' * 64 for name in ['backend', 'frontend', 'policy-api']}}
        self.path = self.root / 'request.json'
        self.cwd = Path.cwd()

    def tearDown(self):
        os.chdir(self.cwd)
        self.directory.cleanup()

    def invoke(self, process):
        self.path.write_text(json.dumps(self.request))
        with patch.object(promotion, 'ROOT', self.root), patch.object(promotion, 'ENV', self.env), \
                patch.object(promotion.sys, 'argv', ['promote.py', str(self.path)]), patch.object(promotion, 'run', side_effect=process):
            promotion.main()

    def test_invalid_registry_cannot_execute_any_process(self):
        self.request['images']['backend'] = 'untrusted.example/attack:latest'
        calls = []
        with self.assertRaises(ValueError):
            self.invoke(lambda args, **kwargs: calls.append(args))
        self.assertEqual(calls, [])
        self.assertEqual(self.env.read_bytes(), self.previous)
        self.assertFalse(self.path.exists())

    def test_failed_readiness_restores_previous_images_and_keeps_credentials_out_of_manifest(self):
        calls = []

        def process(args, **kwargs):
            calls.append(args)
            self.assertNotIn('PRIVATE_REGISTRY_FIXTURE', ' '.join(args))
            if any('SELECT max' in value for value in args):
                return b'13\n'
            if '--entrypoint' in args:
                return b'{"models":"fixture"}\n'
            if args == ['bash', 'deploy/host/health.sh']:
                raise RuntimeError('private provider diagnostic must not escape')
            return b''

        with self.assertRaisesRegex(RuntimeError, '^Promotion failed; operator review required$'):
            self.invoke(process)
        self.assertEqual(self.env.read_bytes(), self.previous)
        self.assertEqual(sum('up' in call for call in calls), 2)
        self.assertFalse((self.root / 'private/current-release.json').exists())
        self.assertFalse(list((self.root / 'private').glob('registry-*')))

    def test_success_records_only_public_release_identifiers(self):
        def process(args, **kwargs):
            if any('SELECT max' in value for value in args):
                return b'13\n'
            if '--entrypoint' in args:
                return b'{"models":"fixture"}\n'
            return b''

        self.invoke(process)
        evidence = (self.root / 'private/current-release.json').read_text()
        self.assertNotIn('PRIVATE_REGISTRY_FIXTURE', evidence)
        self.assertNotIn('fixture-password', evidence)
        self.assertEqual(json.loads(evidence)['images'], self.request['images'])
        self.assertIn(b'@sha256:', self.env.read_bytes())

    def test_schema_upgrade_requires_operator_before_registry_or_database_mutations(self):
        self.request['schema_version'] = 14
        calls = []

        def process(args, **kwargs):
            calls.append(args)
            return b'13\n'

        with self.assertRaisesRegex(ValueError, 'Schema upgrade requires operator'):
            self.invoke(process)
        self.assertEqual(len(calls), 1)
        self.assertEqual(self.env.read_bytes(), self.previous)


if __name__ == '__main__':
    unittest.main()
