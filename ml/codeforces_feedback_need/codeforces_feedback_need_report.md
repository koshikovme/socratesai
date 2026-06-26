# Codeforces Feedback-Need Model Study

This study trains ML models that map a beginner-level Python submission to a mentor state.
Labels are grounded in official Codeforces verdicts, and the split holds out complete problems.

## Dataset

- Eligible rows after filtering: 490357
- Modeled rows: 178414
- Rating maximum: 1200
- Modeled problems: 872
- Problem overlap between train and test: 0

## Class Distribution

| Mentor state | Rows |
|---|---:|
| Accepted | 50000 |
| Semantic debug | 50000 |
| Execution safety | 41375 |
| Efficiency review | 22805 |
| Syntax repair | 14234 |

## Model Comparison

| Model | Accuracy | Balanced accuracy | Macro F1 | Weighted F1 |
|---|---:|---:|---:|---:|
| dummy_majority | 0.2716 | 0.2000 | 0.0854 | 0.1161 |
| context_metrics | 0.3342 | 0.3483 | 0.3102 | 0.3040 |
| source_code | 0.4051 | 0.4248 | 0.4262 | 0.4045 |
| source_context | 0.4039 | 0.4240 | 0.4121 | 0.3941 |
| execution_enriched | 0.7808 | 0.8207 | 0.8137 | 0.7763 |

Best non-execution source model: `source_code`.

## Per-Class F1

| Class | dummy_majority | context_metrics | source_code | source_context | execution_enriched |
|---|---:|---:|---:|---:|---:|
| Accepted | 0.0000 | 0.3141 | 0.3803 | 0.3543 | 0.9157 |
| Semantic debug | 0.4272 | 0.4251 | 0.4116 | 0.4439 | 0.6099 |
| Execution safety | 0.0000 | 0.0781 | 0.3501 | 0.2901 | 0.6114 |
| Efficiency review | 0.0000 | 0.3739 | 0.4398 | 0.4776 | 0.9712 |
| Syntax repair | 0.0000 | 0.3601 | 0.5490 | 0.4945 | 0.9602 |
