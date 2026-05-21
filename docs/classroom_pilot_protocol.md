# Classroom Pilot Protocol

## Goal

Evaluate whether SocratesAI feedback improves debugging progress beyond policy-label prediction.

## Minimum Pilot

- Participants: 10-20 students.
- Design: pre-test, guided practice with SocratesAI, post-test, survey.
- Tasks: 6-12 programming problems with known concepts and test suites.
- Duration: one lab session or two short sessions.

## Event Labels

Each interaction event should include:

- `target_feedback_action`: expert-selected action.
- `feedback_helpful`: whether the feedback was useful to the student.
- `resolved_after_feedback`: whether the student fixed the issue after feedback.
- `fixed_after_ms`: time from feedback to fix.
- `repeated_same_error_after_feedback`: whether the same error appeared again.

## Expert Review

Sample 200-500 interaction events for manual review.

Two reviewers independently label at least 20-30% of the sample. Use the backend endpoint:

```http
POST /api/interactions/{interactionId}/expert-labels
```

Allowed actions:

- `CODE_HIGHLIGHT`
- `GUIDING_QUESTION`
- `CONCEPTUAL_HINT`
- `NO_FEEDBACK`

Then compute reliability:

```http
GET /api/interactions/expert-labels/agreement
```

Report observed agreement and Cohen's kappa in the paper.

## Analysis Plan

Compare:

- rule baseline,
- logistic regression,
- random forest,
- small neural classifier,
- optional XGBoost or LightGBM when installed.

Run ablations:

- without history features,
- without analyzer features,
- without suspicious-region features,
- without last feedback action.

Primary ML metrics:

- accuracy,
- macro F1,
- per-class precision/recall/F1.

Learning/outcome metrics:

- pre-test to post-test improvement,
- fix rate after feedback,
- median time to fix,
- repeated-error rate,
- student helpfulness rating.

## Reporting Boundary

Rule/rubric labels show that the policy selector can reproduce a target policy. Expert labels show annotation quality. Outcome labels and the classroom pilot are required for claims about learning benefit.
