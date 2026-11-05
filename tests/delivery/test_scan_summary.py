import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('scan_summary', Path(__file__).resolve().parents[2] / 'scripts/delivery/scan_summary.py')
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class ScanSummaryTest(unittest.TestCase):
    def summarize(self, value):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'scan.json'
            path.write_text(value)
            output = io.StringIO()
            with patch('sys.argv', ['scan_summary.py', str(path), 'policy-api']), contextlib.redirect_stdout(output):
                status = summary.main()
            return status, json.loads(output.getvalue())

    def test_advisories_fail_the_gate_without_printing_image_environment(self):
        status, value = self.summarize(json.dumps({'Metadata': {'Env': ['PRIVATE_SECRET_SENTINEL']}, 'Results': [
            {'Vulnerabilities': [{'VulnerabilityID': 'CVE-test', 'PkgName': 'apt', 'InstalledVersion': '2.6.1',
                                  'FixedVersion': '2.6.2', 'Severity': 'HIGH'}]},
        ]}))
        self.assertEqual(status, 1)
        self.assertEqual(value['fixedHighCritical'], 1)
        self.assertEqual(value['findings'][0]['FixedVersion'], '2.6.2')
        self.assertNotIn('PRIVATE_SECRET_SENTINEL', json.dumps(value))

    def test_empty_report_passes_and_malformed_report_fails_closed(self):
        self.assertEqual(self.summarize('{"Results":[]}')[0], 0)
        self.assertEqual(self.summarize('not-json-private-data')[0], 2)
