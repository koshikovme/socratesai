from __future__ import annotations

import argparse
import csv
import json
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path


SCENARIOS = [
    ("syntax_missing_semicolon_a", 101, "int x = 1"),
    ("syntax_missing_semicolon_b", 101, "System.out.println(x)"),
    ("syntax_missing_semicolon_c", 101, "int total = 0"),
    ("off_by_one_first", 102, "for (int i = 0; i <= n; i++) { sum += i; }"),
    ("off_by_one_repeat_a", 102, "for (int i = 0; i <= values.length; i++) { sum += values[i]; }"),
    ("off_by_one_repeat_b", 102, "for (int i = 1; i <= n; i++) { result += i; }"),
    ("wrong_condition_first", 103, "while (true) { work(); }"),
    ("wrong_condition_repeat", 103, "while (true) { if (done) break; }"),
    ("unfinished_todo_first", 104, "// TODO\nint value = 0;"),
    ("unfinished_todo_repeat_a", 104, "int value = 0; // TODO implement loop"),
    ("unfinished_todo_repeat_b", 104, "throw new UnsupportedOperationException();"),
    ("complete_return_first", 105, "int value = 1; return value;"),
    ("unknown_partial_logic", 106, "int value = 1; value++;"),
    ("complete_return_second", 105, "int total = 0; return total;"),
    ("syntax_after_progress", 101, "int z = 3"),
    ("off_by_one_after_progress", 102, "for (int i = 0; i <= items.length; i++) { total += i; }"),
    ("unfinished_todo_after_progress", 104, "// TODO retry\nint count = 0;"),
    ("wrong_condition_after_progress", 103, "while (true) { count++; }"),
    ("complete_return_final", 105, "int answer = 42; return answer;"),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18080")
    parser.add_argument("--mode", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--events", type=int, default=570)
    parser.add_argument("--stress-requests", type=int, default=400)
    parser.add_argument("--stress-concurrency", type=int, default=8)
    parser.add_argument("--student-offset", type=int, default=10000)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument(
        "--environment",
        default="Real HTTP Spring Boot process with PostgreSQL database",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    token = register(args.base_url, args.timeout)
    rows, replay_wall_ms = run_replay(args, token)
    stress_rows, stress_wall_ms = run_stress(args, token)

    write_csv(output_dir / "events.csv", rows)
    write_csv(output_dir / "stress.csv", stress_rows)
    summary = build_summary(args, rows, stress_rows, replay_wall_ms, stress_wall_ms)
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (output_dir / "summary.md").write_text(to_markdown(summary), encoding="utf-8")


def register(base_url: str, timeout: float) -> str:
    stamp = int(time.time() * 1000)
    payload = {
        "email": f"real-http-{stamp}@socratesai.test",
        "password": "password123",
        "fullName": "Real HTTP Experiment",
        "role": "STUDENT",
    }
    body = post_json(f"{base_url}/api/auth/register", payload, timeout=timeout)
    return body["token"]


def run_replay(args, token: str) -> tuple[list[dict], int]:
    rows = []
    started = time.perf_counter()
    for index in range(1, args.events + 1):
        scenario_name, task_id, code = SCENARIOS[(index - 1) % len(SCENARIOS)]
        student_id = args.student_offset + ((index - 1) // len(SCENARIOS)) + 1
        attempt_no = ((index - 1) % len(SCENARIOS)) + 1
        rows.append(send_mentor_request(
            args.base_url,
            token,
            args.timeout,
            index,
            args.mode,
            "replay",
            student_id,
            task_id,
            attempt_no,
            scenario_name,
            code,
        ))
    wall_ms = round((time.perf_counter() - started) * 1000)
    return rows, wall_ms


def run_stress(args, token: str) -> tuple[list[dict], int]:
    if args.stress_requests <= 0:
        return [], 0

    started = time.perf_counter()
    rows = []
    with ThreadPoolExecutor(max_workers=args.stress_concurrency) as executor:
        futures = []
        for request_no in range(1, args.stress_requests + 1):
            scenario_name, task_id, code = SCENARIOS[(request_no - 1) % len(SCENARIOS)]
            futures.append(executor.submit(
                send_mentor_request,
                args.base_url,
                token,
                args.timeout,
                request_no,
                args.mode,
                "stress",
                args.student_offset + 100000 + request_no,
                2000 + task_id,
                1,
                scenario_name,
                code,
            ))
        for future in as_completed(futures):
            rows.append(future.result())
    wall_ms = round((time.perf_counter() - started) * 1000)
    rows.sort(key=lambda row: row["event_index"])
    return rows, wall_ms


def send_mentor_request(
    base_url: str,
    token: str,
    timeout: float,
    event_index: int,
    mode: str,
    phase: str,
    student_id: int,
    task_id: int,
    attempt_no: int,
    scenario: str,
    code: str,
) -> dict:
    payload = {
        "studentId": student_id,
        "taskId": task_id,
        "language": "java",
        "code": code,
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

    return {
        "event_index": event_index,
        "mode": mode,
        "phase": phase,
        "student_id": student_id,
        "task_id": task_id,
        "attempt_no": attempt_no,
        "scenario": scenario,
        "status": status,
        "action": body.get("action", ""),
        "error_type": body.get("errorType", ""),
        "compile_success": body.get("compileSuccess", ""),
        "tests_passed": body.get("testsPassed", ""),
        "tests_failed": body.get("testsFailed", ""),
        "analysis_time_ms": body.get("analysisTimeMs", ""),
        "wall_latency_ms": wall_latency_ms,
        "suspicious_region": body.get("suspiciousRegion", ""),
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
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def build_summary(args, rows: list[dict], stress_rows: list[dict], replay_wall_ms: int, stress_wall_ms: int) -> dict:
    ok_rows = [row for row in rows if row["status"] == 200]
    ok_stress = [row for row in stress_rows if row["status"] == 200]
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mode": args.mode,
        "environment": args.environment,
        "replay": summarize_rows(ok_rows, rows, replay_wall_ms),
        "stress": summarize_rows(ok_stress, stress_rows, stress_wall_ms),
        "stress_concurrency": args.stress_concurrency,
    }


def summarize_rows(ok_rows: list[dict], all_rows: list[dict], wall_ms: int) -> dict:
    latencies = [int(row["wall_latency_ms"]) for row in ok_rows]
    analysis = [int(row["analysis_time_ms"]) for row in ok_rows if row["analysis_time_ms"] != ""]
    actions = Counter(row["action"] for row in ok_rows)
    errors = Counter(row["error_type"] for row in ok_rows)
    return {
        "requests": len(all_rows),
        "successful": len(ok_rows),
        "errors": len(all_rows) - len(ok_rows),
        "wall_ms": wall_ms,
        "throughput_rps": round(len(all_rows) / (wall_ms / 1000), 2) if wall_ms else 0,
        "latency_ms": stats(latencies),
        "analysis_time_ms": stats(analysis),
        "action_distribution": dict(sorted(actions.items())),
        "error_type_distribution": dict(sorted(errors.items())),
    }


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
    if not sorted_values:
        return 0
    index = max(0, min(len(sorted_values) - 1, round((pct / 100) * len(sorted_values) + 0.5) - 1))
    return sorted_values[index]


def to_markdown(summary: dict) -> str:
    replay = summary["replay"]
    stress = summary["stress"]
    lines = [
        f"# Real HTTP PostgreSQL Benchmark - {summary['mode']}",
        "",
        f"- Generated at: {summary['generated_at']}",
        f"- Environment: {summary['environment']}",
        f"- Mode: `{summary['mode']}`",
        "",
        "## Replay",
        "",
        "| Requests | Successful | Errors | Wall time | Throughput | Mean | Median | P95 | P99 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        f"| {replay['requests']} | {replay['successful']} | {replay['errors']} | {replay['wall_ms']} ms | {replay['throughput_rps']} req/s | {replay['latency_ms']['mean']} ms | {replay['latency_ms']['median']} ms | {replay['latency_ms']['p95']} ms | {replay['latency_ms']['p99']} ms |",
        "",
        "## Stress",
        "",
        f"- Concurrency: {summary['stress_concurrency']}",
        "",
        "| Requests | Successful | Errors | Wall time | Throughput | Mean | Median | P95 | P99 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        f"| {stress['requests']} | {stress['successful']} | {stress['errors']} | {stress['wall_ms']} ms | {stress['throughput_rps']} req/s | {stress['latency_ms']['mean']} ms | {stress['latency_ms']['median']} ms | {stress['latency_ms']['p95']} ms | {stress['latency_ms']['p99']} ms |",
        "",
        "## Action Distribution",
        "",
        "| Action | Replay events |",
        "|---|---:|",
    ]
    for action, count in replay["action_distribution"].items():
        lines.append(f"| `{action}` | {count} |")
    lines.extend(["", "## Interpretation Boundary", "", "This benchmark measures local real-HTTP prototype behavior with PostgreSQL. It is not a classroom learning-outcome study.", ""])
    return "\n".join(lines)


if __name__ == "__main__":
    main()
