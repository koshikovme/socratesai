import contextlib
import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('publish_basics', ROOT / 'scripts/curriculum/publish_basics.py')
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class CurriculumPublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name)
        self.content = json.loads((ROOT / 'content/basics/problems.json').read_text())
        self.starters = {item['key']: {'java': 'synthetic starter', 'cpp17': 'synthetic starter'} for item in self.content['problems']}
        self.private = {item['key']: {'hiddenTests': [{'inputData': 'synthetic private input', 'expectedOutput': 'synthetic private output'}],
                                    'referenceSolutions': {'java': 'private-reference-marker', 'cpp17': 'private-reference-marker'}} for item in self.content['problems']}
        for name, data in [('starters', self.starters), ('validation', self.private), ('access', {'base_url': 'https://socratesai.duckdns.org', 'accounts': {'teacher': {}}})]:
            (self.folder / (name + '.json')).write_text(json.dumps(data))
        self.arguments = ['publish_basics.py', '--starters-file', str(self.folder / 'starters.json'), '--validation-file', str(self.folder / 'validation.json'),
                          '--state-file', str(self.folder / 'state.json'), '--access-file', str(self.folder / 'access.json')]

    def test_public_pack_has_ten_distinct_exercises_and_no_hidden_or_reference_fields(self):
        self.assertEqual(len(self.content['problems']), 10)
        self.assertEqual(len({item['key'] for item in self.content['problems']}), 10)
        public = json.dumps(self.content)
        self.assertNotIn('hiddenTests', public)
        self.assertNotIn('referenceSolutions', public)
        publisher.check_pack(self.content, self.starters, self.private)

    def test_check_mode_never_logs_private_validation_or_contacts_the_server(self):
        output = io.StringIO()
        with patch.object(sys, 'argv', self.arguments + ['--check']), patch.object(publisher, 'LiveApi') as api, contextlib.redirect_stdout(output):
            publisher.main()
        api.assert_not_called()
        self.assertNotIn('private-reference-marker', output.getvalue())
        self.assertNotIn('synthetic private input', output.getvalue())
        self.assertFalse((self.folder / 'state.json').exists())

    def test_completed_pack_replay_checks_ids_but_does_not_create_verify_or_publish_again(self):
        state = {'ownerId': 1, 'packHash': hashlib.sha256((ROOT / 'content/basics/problems.json').read_bytes()
                 + (self.folder / 'starters.json').read_bytes() + (self.folder / 'validation.json').read_bytes()).hexdigest(), 'problems': {}, 'collections': {}}
        for language_index, language in enumerate(self.content['languages']):
            for index, problem in enumerate(self.content['problems'], 1):
                identifier = language_index * 10 + index
                state['problems'][language + ':' + problem['key']] = {'id': identifier, 'versionId': identifier + 100, 'phase': 'published'}
            state['collections'][language] = {'id': language_index + 1, 'phase': 'published'}
        (self.folder / 'state.json').write_text(json.dumps(state))

        def request(method, path, **kwargs):
            self.assertEqual(method, 'GET')
            identifier = int(path.rsplit('/', 1)[1])
            if '/problems/' in path:
                return {'publishedVersionId': identifier + 100}
            language = self.content['languages'][identifier - 1]
            return {'status': 'PUBLISHED', 'problems': [{'problemId': state['problems'][language + ':' + problem['key']]['id'],
                    'problemVersionId': state['problems'][language + ':' + problem['key']]['versionId']} for problem in self.content['problems']]}

        with patch.object(sys, 'argv', self.arguments), patch.object(publisher, 'LiveApi') as api, contextlib.redirect_stdout(io.StringIO()):
            api.return_value.login.return_value = {'role': 'TEACHER', 'userId': 1, 'token': 'synthetic-token'}
            api.return_value.request.side_effect = request
            publisher.main()
            api.return_value.poll.assert_not_called()


if __name__ == '__main__':
    unittest.main()
