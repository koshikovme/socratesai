# Model And Dataset Readiness For IJEP

## Verdict

The current model and datasets are ready for a solid prototype/system-evaluation article, but not yet for a strong causal learning-gain article.

Use the paper claim as:

> A policy-guided programming mentor can select feedback actions with low latency and can be evaluated reproducibly against rubric action labels.

Do not claim yet:

> The model improves student learning outcomes compared with a teacher, TA, or chatbot.

## Current Evidence

| Evidence item | Current state | Paper strength |
|---|---:|---|
| Real HTTP replay | 570 events | Good system feasibility evidence |
| Concurrent smoke test | 400 requests, concurrency 8 | Good operational stability evidence |
| Valid fixed ML + Gemini problem-suite run | 360 events, 12 problems, 5 fresh cohorts, 0 errors, 95.83% agreement, 95.80% macro F1 | Strong controlled system-evaluation evidence |
| Rubric-labelled problem-suite dataset | 1,080 rows, 12 problems, 180 sessions | Good supervised policy-selection evidence |
| Expanded weak-label dataset | 381,003 rows, 117,232 coding sessions | Good ML pipeline and ablation evidence |
| Model comparison and ablation study | Rule baseline, Logistic Regression, Random Forest, XGBoost, LightGBM, small neural classifier | Good algorithmic comparison evidence |
| Expert human labels | Not collected yet | Missing for stronger education claim |
| Inter-rater agreement | Infrastructure exists, real ratings not collected yet | Missing until two raters label events |
| Student outcome labels | Logging infrastructure exists, real pilot data not collected yet | Missing for learning-effect claim |

## Strongest Current Argument

The strongest defensible article argument is the separation between:

- analyzer signals,
- learner/session state,
- explicit policy action selection,
- final feedback wording.

This is stronger than simply saying "we used an LLM for feedback" because the system can run without an LLM and the pedagogical action is auditable.

## Dataset Interpretation

The 381,003-row expanded dataset is useful because it tests whether the ML pipeline scales beyond the small benchmark and whether different classifiers can reproduce the policy behavior. It uses all available unique solution sessions from `ml/train.csv`, so it is a stronger scale test than the earlier 42,861-row dataset. It is still weakly labelled data. It should be described as "weakly supervised" or "proxy labelled", not as expert-labelled classroom data.

The 1,080-row problem-suite dataset is more important for the IJEP argument because its `target_feedback_action` column comes from the review rubric. This is the dataset to emphasize for action-selection validity.

## Most Recent Valid Runtime Benchmark

The latest valid end-to-end ML benchmark is:

- Results directory: `experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml`
- Events: 360
- Successful events: 360
- Errors: 0
- Agreement with rubric target action: 95.83%
- Macro F1: 95.80%
- Mean latency: 1079.21 ms
- P95 latency: 2072 ms
- Feedback source: 317 Gemini responses, 43 template fallbacks

Important: the earlier 720-event "ML" benchmark should not be used as the primary ML result because the backend-to-policy API JSON contract was wrong at that time and the backend fell back to the rule selector. The fixed benchmark above used snake_case ML payloads and fresh student ids, so it is the current primary result.

## Remaining Weak Spots

The remaining disagreements are concentrated in `lru-cache` and `valid-parentheses`, especially local-completion cases where the expected action is `NO_FEEDBACK` but the model still emits an intervention. The next technical improvement should target the silence/no-feedback decision and add stricter tracing of Gemini/template fallback reasons.

## Minimum Next Step For A Stronger Revision

Collect 200-500 reviewed events from the current app:

1. Let a teacher or TA label `target_feedback_action`.
2. Let a second rater label at least 50-100 overlapping events.
3. Report Cohen's kappa.
4. Retrain/evaluate on those labels.
5. Add outcome labels: helpful, fixed after feedback, fixed time, repeated same error.

With those additions, the paper becomes much stronger because it moves from rubric/proxy labels toward instructor-labelled educational evidence.
