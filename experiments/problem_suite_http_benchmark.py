from __future__ import annotations

import argparse
import csv
import json
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


BASE_ATTEMPTS = [
    {
        "stage": "syntax",
        "scenario": "missing_statement_ending",
        "code": "int candidate = 0",
        "expected_action": "CODE_HIGHLIGHT",
        "expected_reason": "First syntax error should be localized before giving a concept explanation.",
    },
    {
        "stage": "planning",
        "scenario": "unfinished_implementation",
        "code": "// TODO implement the core algorithm\nint progress = 0;",
        "expected_action": "GUIDING_QUESTION",
        "expected_reason": "Unfinished code should prompt the student to state the next step.",
    },
]


PROBLEMS = [
    {
        "id": 301,
        "slug": "palindrome",
        "title": "Valid Palindrome",
        "difficulty": "easy",
        "concept": "two pointers and string normalization",
        "mistake": "possible_null_access",
        "first_code": 'return s.equals(new StringBuilder(s).reverse().toString());',
        "repeat_code": 'String normalized = s.trim(); return normalized.equals(new StringBuilder(normalized).reverse().toString());',
        "complete_code": 'int left = 0; int right = s.length() - 1; while (left < right) { left++; right--; } return true;',
    },
    {
        "id": 302,
        "slug": "two-sum",
        "title": "Two Sum",
        "difficulty": "easy",
        "concept": "hash map lookup and pair search",
        "mistake": "off_by_one",
        "first_code": "for (int i = 0; i <= nums.length; i++) { int need = target - nums[i]; } return new int[]{0, 1};",
        "repeat_code": "for (int j = 0; j <= nums.length; j++) { if (map.containsKey(nums[j])) return new int[]{j, map.get(nums[j])}; } return null;",
        "complete_code": "for (int i = 0; i < nums.length; i++) { if (map.containsKey(target - nums[i])) return new int[]{i, map.get(target - nums[i])}; } return null;",
    },
    {
        "id": 303,
        "slug": "valid-parentheses",
        "title": "Valid Parentheses",
        "difficulty": "easy",
        "concept": "stack state and matching brackets",
        "mistake": "possible_null_access",
        "first_code": "for (char ch : s.toCharArray()) { if (stack.peek() == ch) stack.pop(); } return stack.isEmpty();",
        "repeat_code": "for (char ch : s.toCharArray()) { if (stack.peek() == ')') stack.pop(); } return stack.isEmpty();",
        "complete_code": "for (char ch : s.toCharArray()) { if (!stack.isEmpty()) { stack.pop(); } } return stack.isEmpty();",
    },
    {
        "id": 304,
        "slug": "binary-search",
        "title": "Binary Search",
        "difficulty": "easy",
        "concept": "search interval invariant",
        "mistake": "wrong_loop_boundary",
        "first_code": "int left = 0; int right = nums.length - 1; while (left < right) { int mid = (left + right) / 2; if (nums[mid] == target) return mid; left = mid + 1; } return -1;",
        "repeat_code": "int low = 0; int high = nums.length - 1; while (low < high) { int mid = (low + high) / 2; high = mid - 1; } return -1;",
        "complete_code": "int left = 0; int right = nums.length - 1; while (left <= right) { int mid = (left + right) / 2; return mid; } return -1;",
    },
    {
        "id": 305,
        "slug": "merge-sorted-array",
        "title": "Merge Sorted Array",
        "difficulty": "easy",
        "concept": "array index management",
        "mistake": "off_by_one",
        "first_code": "for (int i = 0; i <= m + n; i++) { nums1[i] = buffer[i]; } return nums1;",
        "repeat_code": "for (int k = 0; k <= nums1.length; k++) { nums1[k] = merged[k]; } return nums1;",
        "complete_code": "for (int k = nums1.length - 1; k >= 0; k--) { nums1[k] = k; } return nums1;",
    },
    {
        "id": 306,
        "slug": "trapping-rain-water",
        "title": "Trapping Rain Water",
        "difficulty": "hard",
        "concept": "two pointers and running maxima",
        "mistake": "wrong_condition",
        "first_code": "int left = 0; int right = height.length - 1; while (true) { water += Math.min(leftMax, rightMax); } return water;",
        "repeat_code": "int left = 0; int right = height.length - 1; while (true) { if (left > right) break; water += height[left]; } return water;",
        "complete_code": "int left = 0; int right = height.length - 1; while (left < right) { left++; right--; } return water;",
    },
    {
        "id": 307,
        "slug": "lru-cache",
        "title": "LRU Cache",
        "difficulty": "medium",
        "concept": "state update and eviction policy",
        "mistake": "unknown_logic",
        "first_code": "cache.put(key, value); return value;",
        "repeat_code": "cache.put(key, value); if (cache.size() > capacity) { } return value;",
        "complete_code": "cache.put(key, value); if (cache.size() > capacity) { cache.remove(oldestKey); } return value;",
    },
    {
        "id": 308,
        "slug": "longest-substring",
        "title": "Longest Substring Without Repeating Characters",
        "difficulty": "medium",
        "concept": "sliding window invariant",
        "mistake": "off_by_one",
        "first_code": "for (int right = 0; right <= s.length(); right++) { best = Math.max(best, right - left); } return best;",
        "repeat_code": "for (int right = 1; right <= s.length(); right++) { seen.add(s.charAt(right)); } return best;",
        "complete_code": "for (int right = 0; right < s.length(); right++) { best = Math.max(best, right - left + 1); } return best;",
    },
    {
        "id": 309,
        "slug": "reverse-linked-list",
        "title": "Reverse Linked List",
        "difficulty": "easy",
        "concept": "pointer update order",
        "mistake": "wrong_condition",
        "first_code": "while (true) { ListNode next = current.next; current.next = prev; } return prev;",
        "repeat_code": "while (true) { if (current == null) break; current = current.next; } return prev;",
        "complete_code": "while (current != null) { ListNode next = current.next; current.next = prev; prev = current; current = next; } return prev;",
    },
    {
        "id": 310,
        "slug": "matrix-diagonal-sum",
        "title": "Matrix Diagonal Sum",
        "difficulty": "easy",
        "concept": "nested indexing and double counting",
        "mistake": "off_by_one",
        "first_code": "for (int i = 0; i <= mat.length; i++) { sum += mat[i][i]; } return sum;",
        "repeat_code": "for (int i = 0; i <= n; i++) { sum += mat[i][n - i]; } return sum;",
        "complete_code": "for (int i = 0; i < mat.length; i++) { sum += mat[i][i]; } return sum;",
    },
    {
        "id": 311,
        "slug": "roman-to-integer",
        "title": "Roman to Integer",
        "difficulty": "easy",
        "concept": "lookahead condition",
        "mistake": "off_by_one",
        "first_code": "for (int i = 0; i <= s.length(); i++) { total += value(s.charAt(i)); } return total;",
        "repeat_code": "for (int i = 1; i <= s.length(); i++) { total += value(s.charAt(i)); } return total;",
        "complete_code": "for (int i = 0; i < s.length(); i++) { total += value(s.charAt(i)); } return total;",
    },
    {
        "id": 312,
        "slug": "climbing-stairs",
        "title": "Climbing Stairs",
        "difficulty": "easy",
        "concept": "base cases and recurrence",
        "mistake": "wrong_condition",
        "first_code": "int a = 1; int b = 1; while (true) { int next = a + b; a = b; b = next; } return b;",
        "repeat_code": "int a = 0; int b = 1; while (true) { if (n == 0) break; n--; } return b;",
        "complete_code": "for (int i = 2; i <= n; i++) { int next = a + b; a = b; b = next; } return b;",
    },
]


MISTAKE_EXPECTATIONS = {
    "off_by_one": ("CODE_HIGHLIGHT", "CONCEPTUAL_HINT"),
    "wrong_loop_boundary": ("CODE_HIGHLIGHT", "CONCEPTUAL_HINT"),
    "possible_null_access": ("CODE_HIGHLIGHT", "CONCEPTUAL_HINT"),
    "wrong_condition": ("CONCEPTUAL_HINT", "CONCEPTUAL_HINT"),
    "unknown_logic": ("CONCEPTUAL_HINT", "CONCEPTUAL_HINT"),
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18080")
    parser.add_argument("--mode", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--cohorts", type=int, default=4)
    parser.add_argument("--student-offset", type=int, default=300000)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument(
        "--environment",
        default="Real HTTP Spring Boot process with PostgreSQL database",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    token = register(args.base_url, args.timeout)
    rows, wall_ms = run_suite(args, token)
    write_csv(output_dir / "events.csv", rows)
    summary = build_summary(args, rows, wall_ms)
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (output_dir / "summary.md").write_text(to_markdown(summary), encoding="utf-8")


def register(base_url: str, timeout: float) -> str:
    stamp = int(time.time() * 1000)
    payload = {
        "email": f"problem-suite-{stamp}@socratesai.test",
        "password": "password123",
        "fullName": "Problem Suite Evaluation",
        "role": "STUDENT",
    }
    body = post_json(f"{base_url}/api/auth/register", payload, timeout=timeout)
    return body["token"]


def run_suite(args, token: str) -> tuple[list[dict], int]:
    rows = []
    started = time.perf_counter()
    event_index = 1
    for cohort in range(1, args.cohorts + 1):
        for problem_index, problem in enumerate(PROBLEMS, start=1):
            student_id = args.student_offset + (cohort * 1000) + problem_index
            attempts = attempts_for(problem)
            for attempt_no, attempt in enumerate(attempts, start=1):
                rows.append(send_mentor_request(
                    args.base_url,
                    token,
                    args.timeout,
                    event_index,
                    args.mode,
                    cohort,
                    student_id,
                    problem,
                    attempt_no,
                    attempt,
                ))
                event_index += 1
    wall_ms = round((time.perf_counter() - started) * 1000)
    return rows, wall_ms


def attempts_for(problem: dict) -> list[dict]:
    first_expected, repeat_expected = MISTAKE_EXPECTATIONS[problem["mistake"]]
    return [
        *BASE_ATTEMPTS,
        {
            "stage": "first_conceptual_error",
            "scenario": f"{problem['mistake']}_first",
            "code": problem["first_code"],
            "expected_action": first_expected,
            "expected_reason": "First substantial mistake should receive the least sufficient intervention.",
        },
        {
            "stage": "repeated_conceptual_error",
            "scenario": f"{problem['mistake']}_repeat",
            "code": problem["repeat_code"],
            "expected_action": repeat_expected,
            "expected_reason": "Repeated mistake should move from location cue to conceptual support.",
        },
        {
            "stage": "partial_logic",
            "scenario": "partial_but_compiling",
            "code": "int checkpoint = 1; checkpoint++;",
            "expected_action": "CONCEPTUAL_HINT",
            "expected_reason": "Compiling but incomplete logic should receive a compact conceptual nudge.",
        },
        {
            "stage": "local_completion",
            "scenario": "locally_complete",
            "code": problem["complete_code"],
            "expected_action": "NO_FEEDBACK",
            "expected_reason": "Locally complete state should not be interrupted.",
        },
    ]


def send_mentor_request(
    base_url: str,
    token: str,
    timeout: float,
    event_index: int,
    mode: str,
    cohort: int,
    student_id: int,
    problem: dict,
    attempt_no: int,
    attempt: dict,
) -> dict:
    payload = {
        "studentId": student_id,
        "taskId": problem["id"],
        "language": "java",
        "code": attempt["code"],
        "attemptNo": attempt_no,
    }
    headers = {"Authorization": f"Bearer {token}"}
    started = time.perf_counter()
    status = 200
    error = ""
    body = {}
    try:
        body = post_json(f"{base_url}/api/mentor/analyze-feedback", payload, headers, timeout)
    except urllib.error.HTTPError as exc:
        status = exc.code
        error = exc.read().decode("utf-8", errors="replace")
    except Exception as exc:
        status = 0
        error = str(exc)
    wall_latency_ms = round((time.perf_counter() - started) * 1000)
    actual_action = body.get("action", "")

    return {
        "event_index": event_index,
        "mode": mode,
        "cohort": cohort,
        "student_id": student_id,
        "problem_id": problem["id"],
        "problem_slug": problem["slug"],
        "problem_title": problem["title"],
        "difficulty": problem["difficulty"],
        "concept": problem["concept"],
        "attempt_no": attempt_no,
        "stage": attempt["stage"],
        "scenario": attempt["scenario"],
        "expected_action": attempt["expected_action"],
        "expected_reason": attempt["expected_reason"],
        "status": status,
        "action": actual_action,
        "agreement": str(actual_action == attempt["expected_action"]).lower() if status == 200 else "false",
        "error_type": body.get("errorType", ""),
        "compile_success": body.get("compileSuccess", ""),
        "tests_passed": body.get("testsPassed", ""),
        "tests_failed": body.get("testsFailed", ""),
        "analysis_time_ms": body.get("analysisTimeMs", ""),
        "wall_latency_ms": wall_latency_ms,
        "suspicious_region": body.get("suspiciousRegion", ""),
        "code_lines": len(attempt["code"].splitlines()),
        "error": error,
    }


def post_json(url: str, payload: dict, headers: dict | None = None, timeout: float = 30.0) -> dict:
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=request_headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def build_summary(args, rows: list[dict], wall_ms: int) -> dict:
    ok_rows = [row for row in rows if row["status"] == 200]
    latencies = [int(row["wall_latency_ms"]) for row in ok_rows]
    analysis = [int(row["analysis_time_ms"]) for row in ok_rows if row["analysis_time_ms"] != ""]
    agreements = [row["agreement"] == "true" for row in ok_rows]
    per_problem = {}
    for problem_slug, problem_rows in group_by(ok_rows, "problem_slug").items():
        problem_agreements = [row["agreement"] == "true" for row in problem_rows]
        per_problem[problem_slug] = {
            "events": len(problem_rows),
            "agreement": round(sum(problem_agreements) / len(problem_agreements), 4) if problem_agreements else 0,
        }

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mode": args.mode,
        "environment": args.environment,
        "cohorts": args.cohorts,
        "problems": len(PROBLEMS),
        "events": len(rows),
        "successful": len(ok_rows),
        "errors": len(rows) - len(ok_rows),
        "wall_ms": wall_ms,
        "throughput_rps": round(len(rows) / (wall_ms / 1000), 2) if wall_ms else 0,
        "agreement": round(sum(agreements) / len(agreements), 4) if agreements else 0,
        "latency_ms": stats(latencies),
        "analysis_time_ms": stats(analysis),
        "action_distribution": dict(sorted(Counter(row["action"] for row in ok_rows).items())),
        "expected_distribution": dict(sorted(Counter(row["expected_action"] for row in ok_rows).items())),
        "error_type_distribution": dict(sorted(Counter(row["error_type"] for row in ok_rows).items())),
        "difficulty_distribution": dict(sorted(Counter(row["difficulty"] for row in ok_rows).items())),
        "per_problem": dict(sorted(per_problem.items())),
        "per_class": per_class_metrics(ok_rows),
    }


def group_by(rows: list[dict], key: str) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        grouped[str(row[key])].append(row)
    return grouped


def stats(values: list[int]) -> dict:
    if not values:
        return {"mean": 0, "median": 0, "p95": 0, "p99": 0, "min": 0, "max": 0}
    values = sorted(values)
    return {
        "mean": round(statistics.mean(values), 2),
        "median": round(statistics.median(values), 2),
        "p95": percentile(values, 95),
        "p99": percentile(values, 99),
        "min": values[0],
        "max": values[-1],
    }


def percentile(sorted_values: list[int], pct: int) -> int:
    index = max(0, min(len(sorted_values) - 1, round((pct / 100) * len(sorted_values) + 0.5) - 1))
    return sorted_values[index]


def per_class_metrics(rows: list[dict]) -> dict:
    labels = sorted({row["expected_action"] for row in rows} | {row["action"] for row in rows})
    metrics = {}
    for label in labels:
        tp = sum(1 for row in rows if row["expected_action"] == label and row["action"] == label)
        fp = sum(1 for row in rows if row["expected_action"] != label and row["action"] == label)
        fn = sum(1 for row in rows if row["expected_action"] == label and row["action"] != label)
        precision = tp / (tp + fp) if tp + fp else 0
        recall = tp / (tp + fn) if tp + fn else 0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0
        metrics[label] = {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": sum(1 for row in rows if row["expected_action"] == label),
        }
    macro_f1 = statistics.mean(value["f1"] for value in metrics.values()) if metrics else 0
    metrics["_macro_f1"] = round(macro_f1, 4)
    return metrics


def to_markdown(summary: dict) -> str:
    lines = [
        f"# Problem Suite HTTP Benchmark - {summary['mode']}",
        "",
        f"- Generated at: {summary['generated_at']}",
        f"- Environment: {summary['environment']}",
        f"- Mode: `{summary['mode']}`",
        f"- Programming problems: {summary['problems']}",
        f"- Cohorts: {summary['cohorts']}",
        "",
        "## Overall",
        "",
        "| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
        f"| {summary['events']} | {summary['successful']} | {summary['errors']} | {summary['agreement']:.2%} | {summary['per_class']['_macro_f1']:.2%} | {summary['latency_ms']['mean']} ms | {summary['latency_ms']['p95']} ms | {summary['throughput_rps']} req/s |",
        "",
        "## Action Distribution",
        "",
        "| Action | Expected | Actual |",
        "|---|---:|---:|",
    ]
    all_actions = sorted(set(summary["expected_distribution"]) | set(summary["action_distribution"]))
    for action in all_actions:
        lines.append(
            f"| `{action}` | {summary['expected_distribution'].get(action, 0)} | "
            f"{summary['action_distribution'].get(action, 0)} |"
        )

    lines.extend([
        "",
        "## Per-Class Metrics",
        "",
        "| Action | Precision | Recall | F1 | Support |",
        "|---|---:|---:|---:|---:|",
    ])
    for action, metrics in summary["per_class"].items():
        if action.startswith("_"):
            continue
        lines.append(
            f"| `{action}` | {metrics['precision']:.2%} | {metrics['recall']:.2%} | "
            f"{metrics['f1']:.2%} | {metrics['support']} |"
        )

    lines.extend([
        "",
        "## Problem Coverage",
        "",
        "| Problem | Events | Agreement |",
        "|---|---:|---:|",
    ])
    for problem, values in summary["per_problem"].items():
        lines.append(f"| `{problem}` | {values['events']} | {values['agreement']:.2%} |")

    lines.extend([
        "",
        "## Interpretation Boundary",
        "",
        "This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.",
        "",
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    main()
