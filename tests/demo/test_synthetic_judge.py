import base64
import importlib.util
import json
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('synthetic_judge', ROOT / 'scripts/demo/synthetic_judge.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SyntheticJudgeTest(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.programs = json.loads((ROOT / 'deploy/demo/programs.json').read_text())
        self.fixture = module.Fixture(self.programs, clock=lambda: self.now)

    def submission(self, code=None, language=1):
        return {'source_code': base64.b64encode((code or self.programs['python3']).encode()).decode(),
                'stdin': base64.b64encode(b'2 3\n').decode(), 'language_id': language, 'enable_network': False}

    def test_exact_templates_produce_only_the_documented_synthetic_response(self):
        tokens = self.fixture.submit([self.submission(self.programs[key], index) for index, key in enumerate(['python3','java','cpp17'], 1)])
        self.assertTrue(all(item['status']['id'] == 2 for item in self.fixture.poll([v['token'] for v in tokens])['submissions']))
        self.now = 2
        results = self.fixture.poll([v['token'] for v in tokens])['submissions']
        self.assertTrue(all(base64.b64decode(v['stdout']) == b'5\n' and v['status']['id'] == 3 for v in results))

    def test_arbitrary_student_source_is_never_executed(self):
        with patch('os.system') as execute:
            token = self.fixture.submit([self.submission('__import__("os").system("echo DO_NOT_EXECUTE")')])[0]['token']
            self.now = 2
            self.assertIn(b'Synthetic demo', base64.b64decode(self.fixture.poll([token])['submissions'][0]['stdout']))
            execute.assert_not_called()
        self.assertNotIn('source_code', self.fixture.results[token])

    def test_invalid_or_oversized_input_never_partially_creates_a_batch(self):
        for submissions in [[], [self.submission()] * 21, [self.submission('x' * 80001)], [self.submission(language=999)]]:
            with self.subTest(size=len(submissions)):
                with self.assertRaises((ValueError, KeyError)):
                    self.fixture.submit(submissions)
                self.assertFalse(self.fixture.results)
        bad = self.submission(); bad['enable_network'] = True
        with self.assertRaises(ValueError): self.fixture.submit([bad])

    def test_slow_fixture_and_expiry_are_bounded_without_retaining_source(self):
        token = self.fixture.submit([self.submission('# DEMO_RESTART\n' + self.programs['python3'])])[0]['token']
        self.now = 11
        self.assertEqual(2, self.fixture.poll([token])['submissions'][0]['status']['id'])
        self.now = 13
        self.assertEqual(3, self.fixture.poll([token])['submissions'][0]['status']['id'])
        self.now = 901
        self.fixture.submit([self.submission()])
        self.assertNotIn(token, self.fixture.results)

    def test_fixture_cannot_start_without_an_explicit_demo_switch(self):
        with patch.dict(module.os.environ, {}, clear=True), self.assertRaises(SystemExit): module.serve()


if __name__ == '__main__':
    unittest.main()
