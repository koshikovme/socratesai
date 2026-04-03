from __future__ import annotations

import argparse
import ast
import random
from pathlib import Path

import pandas as pd

SESSION_TEMPLATES = [
    [
        ('SYNTAX_ERROR', 'HIGH', False, 0, 1, 'CODE_HIGHLIGHT'),
        ('WRONG_CONDITION', 'MEDIUM', True, 0, 1, 'CONCEPTUAL_HINT'),
        ('WRONG_CONDITION', 'MEDIUM', True, 0, 1, 'GUIDING_QUESTION'),
        ('SUCCESS', 'LOW', True, 1, 0, 'NO_FEEDBACK'),
    ],
    [
        ('SYNTAX_ERROR', 'HIGH', False, 0, 1, 'CODE_HIGHLIGHT'),
        ('SYNTAX_ERROR', 'HIGH', False, 0, 1, 'CONCEPTUAL_HINT'),
        ('SUCCESS', 'LOW', True, 1, 0, 'NO_FEEDBACK'),
    ],
    [
        ('OFF_BY_ONE', 'MEDIUM', True, 0, 1, 'CODE_HIGHLIGHT'),
        ('OFF_BY_ONE', 'MEDIUM', True, 0, 1, 'CONCEPTUAL_HINT'),
        ('SUCCESS', 'LOW', True, 1, 0, 'NO_FEEDBACK'),
    ],
    [
        ('STUCK_NO_PROGRESS', 'MEDIUM', True, 0, 1, 'GUIDING_QUESTION'),
        ('WRONG_CONDITION', 'MEDIUM', True, 0, 1, 'CONCEPTUAL_HINT'),
        ('SUCCESS', 'LOW', True, 1, 0, 'NO_FEEDBACK'),
    ],
]


def parse_solutions(raw: object) -> list[str]:
    if raw is None or (isinstance(raw, float) and pd.isna(raw)):
        return []

    text = str(raw).strip()
    if not text:
        return []

    try:
        parsed = ast.literal_eval(text)
    except Exception:
        return []

    if isinstance(parsed, list):
        return [str(item) for item in parsed if str(item).strip()]
    return []


def estimate_code_lines(code: str, starter_code: object) -> int:
    baseline = code or str(starter_code or '')
    lines = [line for line in baseline.splitlines() if line.strip()]
    return max(1, len(lines))


def difficulty_to_seed(value: object) -> int:
    return sum(ord(ch) for ch in str(value or ''))


def build_rows(df: pd.DataFrame, max_problems: int, solutions_per_problem: int) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    session_id = 0

    for _, row in df.head(max_problems).iterrows():
        solutions = parse_solutions(row.get('solutions'))
        if not solutions:
            continue

        for solution_index, code in enumerate(solutions[:solutions_per_problem]):
            template_index = (int(row.get('problem_id', 0)) + solution_index + difficulty_to_seed(row.get('difficulty'))) % len(SESSION_TEMPLATES)
            template = SESSION_TEMPLATES[template_index]
            code_lines = estimate_code_lines(code, row.get('starter_code'))

            previous_action = None
            total_errors_seen = 0

            for attempt_no, (error_type, severity, compile_success, tests_passed, tests_failed, action) in enumerate(template, start=1):
                if error_type != 'SUCCESS':
                    total_errors_seen += 1

                same_error_count = 0
                if attempt_no > 1:
                    previous_error_type = template[attempt_no - 2][0]
                    if error_type == previous_error_type and error_type != 'SUCCESS':
                        same_error_count = sum(1 for step in template[:attempt_no] if step[0] == error_type)
                    elif error_type != 'SUCCESS':
                        same_error_count = 1

                if attempt_no == 1:
                    last_feedback_success = None
                else:
                    last_feedback_success = template[attempt_no - 2][0] == 'SUCCESS'

                rows.append(
                    {
                        'student_id': random.randint(1, 200),
                        'problem_id': row.get('problem_id'),
                        'difficulty': row.get('difficulty'),
                        'dataset_source': 'public_code_corpus',
                        'session_id': session_id,
                        'error_type': error_type,
                        'severity': severity,
                        'compile_success': compile_success,
                        'tests_passed': tests_passed,
                        'tests_failed': tests_failed,
                        'same_error_count': same_error_count,
                        'total_errors_seen': total_errors_seen,
                        'attempt_no': attempt_no,
                        'last_feedback_action': previous_action,
                        'last_feedback_success': last_feedback_success,
                        'has_suspicious_region': error_type != 'SUCCESS',
                        'code_lines': code_lines,
                        'total_feedback_count_in_session': attempt_no - 1,
                        'feedback_action': action,
                    }
                )
                previous_action = action

            session_id += 1

    return rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description='Build a policy dataset from a public code corpus of problems and accepted solutions.'
    )
    parser.add_argument('--input', default='train.csv', help='Path to code corpus CSV')
    parser.add_argument('--output', default='policy_dataset.csv', help='Output CSV path')
    parser.add_argument('--max-problems', type=int, default=1000, help='Maximum number of problems to use')
    parser.add_argument('--solutions-per-problem', type=int, default=3, help='Maximum accepted solutions to use per problem')
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output)

    if not input_path.exists():
        raise FileNotFoundError(f'Input CSV not found: {input_path.resolve()}')

    df = pd.read_csv(input_path, usecols=['problem_id', 'solutions', 'difficulty', 'starter_code'])
    rows = build_rows(df, max_problems=args.max_problems, solutions_per_problem=args.solutions_per_problem)
    if not rows:
        raise ValueError('No usable rows were produced. Check that the dataset has a parseable `solutions` column.')

    dataset = pd.DataFrame(rows)
    dataset.to_csv(output_path, index=False)

    sessions = dataset['session_id'].nunique()
    print(f'Problems used: {min(args.max_problems, len(df))}')
    print(f'Coding sessions: {sessions}')
    print(f'Output rows: {len(dataset)}')
    print('Action distribution:')
    print(dataset['feedback_action'].value_counts(dropna=False))
    print(f'Saved policy dataset to: {output_path.resolve()}')


if __name__ == '__main__':
    main()
