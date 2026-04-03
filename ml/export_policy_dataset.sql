-- Weak-supervision export from interaction_logs.
-- feedback_action is the current policy decision, so it is a weak label.
-- For stronger supervision add target_feedback_action manually after review.

SELECT
    interaction_id,
    student_id,
    task_id,
    policy_version,
    created_at,
    error_type,
    severity,
    COALESCE(compile_success, FALSE) AS compile_success,
    COALESCE(tests_passed, 0) AS tests_passed,
    COALESCE(tests_failed, 0) AS tests_failed,
    COALESCE(same_error_count, 0) AS same_error_count,
    COALESCE(total_errors_seen, 0) AS total_errors_seen,
    COALESCE(attempt_no, 0) AS attempt_no,
    NULLIF(last_feedback_action, '') AS last_feedback_action,
    last_feedback_success,
    COALESCE(has_suspicious_region, FALSE) AS has_suspicious_region,
    COALESCE(code_lines, 0) AS code_lines,
    COALESCE(total_feedback_count_in_session, 0) AS total_feedback_count_in_session,
    feedback_action,
    resolved_after_feedback,
    fixed_after_ms,
    suspicious_region
FROM interaction_logs
WHERE feedback_action IS NOT NULL
ORDER BY created_at;
