"""Language/privacy contracts and saved-model smoke inference, never training."""
import unittest
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
import policy_api as api


class FixtureModel:
    classes_ = ["semantic_debug", "accepted"]
    calls = 0

    def predict(self, frame):
        self.calls += 1
        self.frame = frame
        return ["semantic_debug"]

    def predict_proba(self, frame):
        return [[0.8, 0.2]]


class PolicyApiContractTest(unittest.TestCase):
    def setUp(self):
        self.model = FixtureModel()
        self.override = patch.object(api, "mentor_state_model", self.model)
        self.override.start()
        self.client = TestClient(api.app)

    def tearDown(self):
        self.client.close()
        self.override.stop()

    def test_only_canonical_python_reaches_inference(self):
        for key in ["java", "cpp17", "python", "Python 3", "", None]:
            with self.subTest(key=key):
                response = self.client.post("/predict-mentor-state", json={"language_key": key, "code": "private-student-buffer"})
                self.assertEqual(422, response.status_code)
                self.assertNotIn("private-student-buffer", response.text)
        response = self.client.post("/predict-mentor-state", json={"language_key": "python3", "language_variant": "Java 21", "code": "private-student-buffer"})
        self.assertEqual(422, response.status_code)
        self.assertEqual(0, self.model.calls)

    def test_missing_language_fails_closed(self):
        response = self.client.post("/predict-mentor-state", json={"code": "private-student-buffer"})
        self.assertEqual(422, response.status_code)
        self.assertNotIn("private-student-buffer", response.text)
        self.assertEqual(0, self.model.calls)

    def test_validation_errors_do_not_reflect_source(self):
        response = self.client.post("/predict-mentor-state", json={"language_key": "python3", "code": "private-student-buffer" * 2000})
        self.assertEqual(422, response.status_code)
        self.assertEqual({"code": "INVALID_REQUEST", "message": "Invalid inference request"}, response.json())

    def test_supported_inference_preserves_unknown_execution_and_text_truncation(self):
        response = self.client.post("/predict-mentor-state", json={"language_key": "python3", "code": "#" * 6000, "execution_known": False,
                                                                  "tests_passed": None, "compile_success": None})
        self.assertEqual(200, response.status_code)
        self.assertEqual("semantic_debug", response.json()["mentor_state"])
        self.assertEqual(5000, len(self.model.frame.iloc[0]["code_model_text"]))
        self.assertIsNone(self.model.frame.iloc[0]["passed_test_count"])
        self.assertEqual(6000, self.model.frame.iloc[0]["code_chars"])

    def test_trailing_line_endings_use_the_training_metric_definition(self):
        # train_codeforces_feedback_need_model.add_code_metrics counts LF + 1,
        # including a final empty line; splitlines() alone loses that evidence.
        metrics = api.source_metrics("print(1)\n")
        self.assertEqual(2, metrics["code_lines"])
        self.assertEqual(1, metrics["blank_line_count"])
        self.assertEqual(8, metrics["max_line_length"])

    def test_unavailable_model_has_a_safe_error(self):
        with patch.object(api, "mentor_state_model", None):
            response = self.client.post("/predict-mentor-state", json={"language_key": "python3", "code": "private-student-buffer"})
            self.assertEqual(503, response.status_code)
            self.assertNotIn("private-student-buffer", response.text)


class SavedModelInferenceSmokeTest(unittest.TestCase):
    def test_default_saved_artifacts_load_and_predict_without_retraining(self):
        base = Path(__file__).resolve().parent
        with patch.object(api, "MODEL_PATH", base / "mentor_policy_model.joblib"), patch.object(api, "MENTOR_STATE_MODEL_PATH", base / "codeforces_feedback_need" / "source_code_model.joblib"), TestClient(api.app) as client:
            self.assertTrue(client.get("/health").json()["mentor_state_model_loaded"])
            response = client.post("/predict-mentor-state", json={"language_key": "python3", "language_variant": "Python 3", "code": "a, b = map(int, input().split())\nprint(a + b)"})
            self.assertEqual(200, response.status_code)
            self.assertIn(response.json()["mentor_state"], api.MENTOR_STATE_TO_ACTION)
            self.assertGreaterEqual(response.json()["confidence"], 0.0)
            self.assertLessEqual(response.json()["confidence"], 1.0)


if __name__ == "__main__":
    unittest.main()
