"""Serving lifecycle/readiness privacy. Uses saved artifacts or synthetic failures, never training."""
import unittest
import json
import tempfile
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
import policy_api as api


class ReadinessTest(unittest.TestCase):
    def test_readiness_is_unavailable_until_both_models_are_loaded(self):
        with patch.object(api, "action_model", None), patch.object(api, "mentor_state_model", None):
            client = TestClient(api.app)
            self.assertEqual(200, client.get('/live').status_code)
            for path in ['/health', '/ready']:
                self.assertEqual(503, client.get(path).status_code)
            api.action_model = object()
            self.assertEqual(503, client.get('/ready').status_code)
            api.mentor_state_model = object()
            self.assertEqual(200, client.get('/ready').status_code)

    def test_partial_load_never_publishes_readiness_and_hides_internal_failure(self):
        with patch.object(api.joblib, 'load', side_effect=[object(), ValueError('PRIVATE_ARTIFACT_PATH')]):
            with self.assertRaisesRegex(RuntimeError, '^Policy model initialization failed$'):
                api.load_model()
            self.assertIsNone(api.action_model)
            self.assertIsNone(api.mentor_state_model)

    def test_checksum_failure_prevents_deserialization(self):
        with patch.dict(api.os.environ, {'MODEL_CHECKSUM_MANIFEST': '/unavailable/PRIVATE_PATH'}), patch.object(api.joblib, 'load') as load:
            with self.assertRaisesRegex(RuntimeError, '^Policy model initialization failed$'):
                api.load_model()
            load.assert_not_called()

    def test_modified_model_checksum_fails_before_joblib_load(self):
        with tempfile.TemporaryDirectory() as folder:
            manifest = Path(folder) / 'manifest.json'
            manifest.write_text(json.dumps({'action': {'sha256': '0' * 64}, 'mentor_state': {'sha256': '0' * 64}}))
            with patch.dict(api.os.environ, {'MODEL_CHECKSUM_MANIFEST': str(manifest)}), patch.object(api.joblib, 'load') as load:
                with self.assertRaisesRegex(RuntimeError, '^Policy model initialization failed$'):
                    api.load_model()
                load.assert_not_called()


if __name__ == '__main__':
    unittest.main()
