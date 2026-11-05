"""Print advisory identifiers/versions only; never dump an image configuration or environment."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('image')
    args = parser.parse_args()
    try:
        report = json.loads(args.report.read_text())
        results = report['Results']
        findings = [
            {key: value.get(key) for key in ('VulnerabilityID', 'PkgName', 'InstalledVersion', 'FixedVersion', 'Severity')}
            for result in results for value in result.get('Vulnerabilities', [])
            if value.get('Severity') in ('HIGH', 'CRITICAL') and value.get('FixedVersion')
        ]
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        print(json.dumps({'image': args.image, 'error': 'Security report missing or invalid'}))
        return 2
    print(json.dumps({'image': args.image, 'fixedHighCritical': len(findings), 'findings': findings}))
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
