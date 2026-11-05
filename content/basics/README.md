# Beginner Java and C++ collections

`problems.json` defines ten introductory exercises: arithmetic, conditions, loops, digits,
arrays and strings. Each language collection contains its own ten native problems, with
named input parameters and a complete stdin/stdout adapter. Students implement the TODO
function and return the answer. Statements are Russian; identifiers/output tokens are explicit.

The public pack contains conditions, parameter contracts and sample cases only. Author
reference programs and hidden suites belong in a protected external validation file and
never enter frontend assets, mentor prompts or student DTOs. This is prepared teaching
material, not evidence of educational effectiveness.

Generate the language scaffolds without compiling or executing programs:

```bash
node scripts/curriculum/build_starters.mjs --output .delivery/basics-starters.json
python3 scripts/curriculum/publish_basics.py --check \
  --starters-file .delivery/basics-starters.json --validation-file .delivery/basics-validation.json \
  --state-file .delivery/basics-publication.json
python3 scripts/curriculum/publish_basics.py --access-file .delivery/production-access.json \
  --starters-file .delivery/basics-starters.json --validation-file .delivery/basics-validation.json \
  --state-file .delivery/basics-publication.json
```

The authorized import uses the normal staff authoring/reference-verification/publication
API and records IDs/phases under `.delivery/`. Every reference program must pass all sample
and hidden cases on the dedicated executor before the version or collection is published.
No schema migration, local student-code execution, fake verdict or manual grade is used.
