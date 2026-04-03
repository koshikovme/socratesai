# Assignment 2: Test Automation Implementation

## 1. Project Title

**Socrates AI: Automated Test Implementation for the Virtual Mentor Backend**

## 2. Purpose of the Assignment

The purpose of this assignment is to introduce automated testing into the Socrates AI backend and verify critical business logic of the virtual mentor system. The implemented test automation focuses on the most important non-trivial components of the backend:
- pedagogical policy selection;
- policy guardrails and ML fallback behavior;
- feedback template generation;
- interaction logging and dataset export;
- REST controller behavior through MockMvc;
- automated execution in CI.

The goal is not only to document a QA strategy, but to implement executable automated tests that can be run with Maven.

## 3. Scope of Test Automation

The test automation scope was selected to cover core business logic without depending on external infrastructure such as:
- PostgreSQL;
- Liquibase migrations;
- Gemini / OpenAI APIs;
- the external ML prediction microservice.

This means the implemented tests are primarily **unit tests and lightweight controller tests** with mocked dependencies where necessary.

## 4. Backend Areas Covered

The automated test suite covers the following backend modules located in `src/main/java/com/masters/socratesai/`:
- `mentor/policy`
- `mentor/feedback`
- `interaction/service`
- `mentor/controller`
- `interaction/controller`

These modules are central to the mentoring workflow because they determine:
- what action should be selected;
- how safety constraints are applied;
- what feedback text is returned for rule/template mode;
- how runtime interaction data is persisted and exported;
- how public REST endpoints serialize and return backend results.

## 5. Implemented Tasks

The following tasks were implemented as part of the assignment.

### Task 1. Add test infrastructure

A proper automated test dependency was added to the Maven build:
- `spring-boot-starter-test`

This provides:
- JUnit 5;
- AssertJ;
- Mockito;
- Spring test support when needed.

### Task 2. Implement unit tests for rule-based policy selection

A dedicated test class was added for the rule-based selector:
- `RuleBasedPolicyEngineTest`

Covered scenarios:
- syntax error leads to `CODE_HIGHLIGHT`;
- repeated off-by-one error leads to `CONCEPTUAL_HINT`;
- `STUCK_NO_PROGRESS` leads to `GUIDING_QUESTION`;
- successful compile and zero failed tests lead to `NO_FEEDBACK`;
- unknown/default states lead to `CONCEPTUAL_HINT`.

### Task 3. Implement unit tests for policy guardrails

A dedicated test class was added for policy safety rules:
- `PolicyGuardrailServiceTest`

Covered scenarios:
- repeated errors prevent `NO_FEEDBACK` and escalate to `CONCEPTUAL_HINT`;
- syntax errors prevent `GUIDING_QUESTION` and force `CODE_HIGHLIGHT`.

### Task 4. Implement unit tests for ML fallback behavior

A dedicated test class was added for the policy orchestration service:
- `MentorPolicyServiceTest`

Covered scenarios:
- rule mode uses the rule-based engine directly;
- ML mode falls back to rule mode if the ML selector throws an exception and fallback is enabled;
- ML mode throws an exception if fallback is disabled.

### Task 5. Implement unit tests for feedback template generation

A dedicated test class was added for the template-based feedback layer:
- `FeedbackTemplateServiceTest`

Covered scenarios:
- off-by-one errors produce the expected conceptual hint;
- code highlighting includes the suspicious region in the generated message;
- `NO_FEEDBACK` produces the expected neutral progress message.

### Task 6. Implement unit tests for interaction logging and dataset export

A dedicated test class was added for the interaction log service:
- `InteractionLogServiceTest`

Covered scenarios:
- interaction saving correctly computes total latency and derived fields;
- student context is correctly reconstructed from recent interaction history;
- exported CSV escapes commas and quotes correctly;
- interaction result update correctly stores final outcome values.

### Task 7. Replace fragile default context test

The original generated `contextLoads()` test required a live PostgreSQL and Liquibase environment. This made the suite non-reproducible for local automation.

It was replaced by a lightweight smoke test:
- `SocratesaiApplicationTests`

This keeps the suite stable while avoiding infrastructure-dependent failures in a unit-test run.

### Task 8. Implement MockMvc tests for REST controllers

Lightweight controller-level tests were added using standalone MockMvc setup, without starting the full Spring context and without depending on authentication infrastructure.

Dedicated test classes:
- `MentorControllerTest`
- `InteractionControllerTest`

Covered scenarios:
- mentor feedback endpoint returns the expected JSON response;
- mentor analyze-feedback endpoint returns the expected action and suspicious region;
- interaction result endpoint updates and returns final outcome data;
- policy dataset export endpoint returns a CSV attachment with the correct filename and payload.

### Task 9. Add CI workflow for automated test execution

A GitHub Actions workflow was added to run backend tests automatically on repository pushes and pull requests.

Implemented workflow:
- `.github/workflows/backend-tests.yml`

The workflow:
- checks out the repository;
- installs Temurin Java 21;
- caches Maven dependencies;
- runs `mvn -B test`.

## 6. Files Added or Updated

### Updated
- `pom.xml`
- `src/test/java/com/masters/socratesai/SocratesaiApplicationTests.java`
- `Assignment_2_Test_Automation_Implementation.md`

### Added
- `src/test/java/com/masters/socratesai/mentor/policy/RuleBasedPolicyEngineTest.java`
- `src/test/java/com/masters/socratesai/mentor/policy/PolicyGuardrailServiceTest.java`
- `src/test/java/com/masters/socratesai/mentor/policy/MentorPolicyServiceTest.java`
- `src/test/java/com/masters/socratesai/mentor/feedback/FeedbackTemplateServiceTest.java`
- `src/test/java/com/masters/socratesai/interaction/service/InteractionLogServiceTest.java`
- `src/test/java/com/masters/socratesai/mentor/controller/MentorControllerTest.java`
- `src/test/java/com/masters/socratesai/interaction/controller/InteractionControllerTest.java`
- `.github/workflows/backend-tests.yml`

## 7. Test Execution

The test suite is executed with:

```bash
mvn test
```

For this project, Maven tests were executed in a Java 21 environment.

If Maven is bound to an older JDK through `JAVA_HOME`, the project now fails early with an explicit version check. A helper script is also available:

```powershell
.\scripts\run-tests-jdk21.ps1
```

## 8. Test Results

The implemented automated test suite completed successfully.

### Final result
- **Tests run:** `22`
- **Failures:** `0`
- **Errors:** `0`
- **Skipped:** `0`

This confirms that the implemented business-logic automation works correctly for the covered scenarios.

## 9. Test Design Rationale

The chosen test strategy is based on the principle of **maximum value with minimal infrastructure dependency**.

This is appropriate for Socrates AI because:
- the most important risks are in decision logic, not boilerplate code;
- the backend integrates with external services that should not be called in unit tests;
- policy selection and interaction logging are the key research-relevant components;
- stable unit and lightweight controller tests are more useful at this stage than brittle environment-dependent full integration tests.

## 10. Current Limitations of the Test Suite

Although the implemented automation significantly improves project quality, several limitations remain.

Not covered yet:
- full Spring context integration testing with a test database;
- WebSocket end-to-end tests;
- frontend automated tests;
- full integration tests for Gemini / OpenAI providers;
- full integration tests for the external ML predictor service;
- enforcement of CI quality gates such as coverage thresholds or branch protection rules.

These should be considered future QA extensions rather than blockers for the current assignment.

## 11. Suggested Next QA Steps

To extend the current automation baseline, the next useful tasks would be:
- add repository integration tests with a dedicated test database or Testcontainers;
- add contract tests for the ML predictor API;
- add frontend component and view tests;
- add WebSocket workflow tests for real-time mentoring;
- add a stricter CI pipeline with branch protection and build status enforcement.

## 12. Conclusion

This assignment introduced real automated tests into the Socrates AI backend and verified the most important parts of the mentoring logic. The implemented suite now covers policy rules, guardrails, fallback logic, template generation, interaction logging, and REST controller behavior, and it is wired into a GitHub Actions workflow for automatic execution. As a result, the project now has an executable test automation baseline that supports both engineering quality and research reproducibility.
