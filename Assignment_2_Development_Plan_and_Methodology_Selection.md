# Assignment 2: Development Plan & Methodology Selection

## 1. Project Title

**Socrates AI: Virtual Mentor System for Introductory Programming with Real-Time Personalized Feedback**

## 2. Project Context

Socrates AI is a software system designed to support first-year programming students during code writing and debugging. The system acts as a virtual mentor: it analyzes the student code, tracks lightweight learning context, selects an appropriate pedagogical intervention, and generates short feedback in real time.

The project is implemented as a multi-layer architecture with three main runtime parts:
- **Web/IDE Frontend** for student interaction
- **Backend Virtual Mentor System** for analysis, session tracking, policy selection, and feedback generation
- **Policy ML Service** for optional machine learning-based action selection

The project is directly related to the master's thesis topic: **Research and implementation of Virtual Mentor models in programming with real-time personalized feedback for first-year students**.

## 3. Aim of the Project

The aim of the project is to design and implement a virtual mentoring system that can provide pedagogically constrained, real-time, personalized feedback to novice programming students during problem solving.

## 4. Objectives

The development objectives are:
- design a modular architecture for a real-time mentoring workflow;
- implement source-code analysis and error-signal extraction;
- model lightweight student context using attempt history and prior feedback outcomes;
- implement a pedagogical policy layer that selects the next feedback action;
- integrate rule-based and ML-based policy variants;
- implement feedback realization using templates and external LLM services;
- store interaction logs for evaluation and future model improvement;
- evaluate the system in terms of feasibility, latency, and action-selection behavior.

## 5. Existing System Basis

The current implementation already contains the core layers required by the project.

### Backend modules

Located in `src/main/java/com/masters/socratesai/`:
- `analyzer` — code analysis, syntax and heuristic checks;
- `mentor` — mentoring workflow orchestration;
- `mentor/policy` — rule-based and ML-based policy selection;
- `mentor/feedback` — template-based and LLM-based feedback generation;
- `interaction` — interaction logging and dataset export;
- `session` — student task session tracking;
- `websocket` — real-time communication with the frontend;
- `auth`, `security`, `user`, `task` — application infrastructure.

### Frontend modules

Located in `socratesai-frontend/src/`:
- `views` — application pages;
- `components` — reusable UI components;
- `services` — REST and WebSocket integration;
- `stores` — state management;
- `router` — navigation.

### ML modules

Located in `ml/`:
- `train_policy_model.py` — policy model training pipeline;
- `policy_api.py` — FastAPI inference service;
- dataset builders and exported datasets;
- serialized model artifacts.

## 6. Methodology Selection

For this project, a **combined methodology** is the most appropriate choice:
- **Design Science Research (DSR)** as the research methodology;
- **Incremental / Agile Prototyping** as the software development methodology;
- **Advanced Quality Assurance (QA)** for systematic verification and validation;
- **Experimental evaluation** for assessing the artifact.

### 6.1 Why Design Science Research

Design Science Research is appropriate because the thesis is centered on the creation and evaluation of an artifact rather than only on theoretical analysis. The main research output is a working virtual mentor system with a defined architecture, pedagogical policy layer, and evaluation pipeline.

DSR fits this project because it supports:
- problem identification;
- artifact design;
- implementation;
- iterative refinement;
- evaluation of the artifact in practice.

### 6.2 Why Incremental / Agile Prototyping

The project requirements evolve during development because the system combines educational logic, backend engineering, frontend interaction, and ML experimentation. A rigid linear methodology would be inefficient.

Incremental development is suitable because it allows:
- implementation of a baseline system first;
- gradual addition of real-time features;
- later integration of ML as an extension rather than an initial dependency;
- testing of each module independently;
- continuous refinement of the architecture.

### 6.3 Why Advanced QA

This project is not only a prototype, but a multi-service system with real-time requirements. Therefore, quality assurance must go beyond simple code compilation. The project needs structured QA for:
- backend correctness;
- service integration;
- real-time communication reliability;
- dataset consistency;
- ML service compatibility;
- failure handling and fallback behavior.

### 6.4 Why Experimental Evaluation

The system must be evaluated not only by code completion, but by runtime and pedagogical behavior. Therefore, experimental evaluation is needed to measure:
- response latency;
- correctness or consistency of action selection;
- observed interaction outcomes;
- feasibility of the real-time mentoring pipeline.

## 7. Chosen Development Strategy

The development strategy is divided into two connected tracks.

### Track A: Engineering track
- build a stable full-stack mentoring platform;
- separate analysis, student context, policy, and feedback generation;
- support both rule-based and ML-based policy selection;
- ensure clean API boundaries between frontend, backend, and ML service.

### Track B: Research track
- define measurable system objectives;
- collect and structure interaction data;
- compare baseline rule-based logic with learned policy behavior;
- document limitations and feasibility results for the thesis and article.

## 8. System Development Plan

### Phase 1. Problem definition and requirements analysis

Tasks:
- analyze the educational problem faced by first-year programming students;
- identify the role of immediate personalized feedback;
- define functional and non-functional requirements.

Outputs:
- project scope;
- system requirements;
- high-level architecture.

### Phase 2. Baseline architecture and backend implementation

Tasks:
- implement backend modules for code analysis;
- create DTOs and API endpoints;
- add session management and interaction logging;
- implement baseline rule-based policy selection.

Outputs:
- working backend mentor pipeline;
- database schema;
- interaction log model.

### Phase 3. Frontend and real-time interaction

Tasks:
- implement IDE-oriented frontend workflow;
- connect REST and WebSocket communication;
- display feedback, action type, and problem state;
- support authentication and session continuity.

Outputs:
- usable frontend for real-time mentoring;
- end-to-end flow from student input to feedback output.

### Phase 4. Feedback generation layer

Tasks:
- implement template-based feedback generation;
- integrate LLM-based services for improved phrasing;
- ensure pedagogical constraints on the generated response.

Outputs:
- configurable feedback generator;
- support for external provider integration.

### Phase 5. ML policy integration

Tasks:
- define policy feature space;
- export or generate training datasets;
- train a preliminary policy classifier;
- deploy the predictor as a microservice;
- integrate ML mode into the backend policy selector.

Outputs:
- ML-ready policy pipeline;
- model artifact and inference API;
- configurable `rule/ml` policy mode.

### Phase 6. Evaluation and refinement

Tasks:
- measure latency and overall runtime behavior;
- analyze feedback-action distribution and system outcomes;
- refine architecture and documentation;
- prepare thesis and article materials.

Outputs:
- evaluation results;
- documented limitations;
- final thesis-oriented implementation.

## 9. Methodology for Model Development

The ML component is not used for raw code analysis. Instead, it is used only in the **pedagogical policy layer**.

The workflow is:
1. analyze code and produce structured signals;
2. combine them with student/session context;
3. build a policy feature vector;
4. predict the next feedback action;
5. realize that action as textual feedback.

The current policy model is a supervised multi-class classifier trained on tabular features such as:
- `error_type`;
- `severity`;
- `compile_success`;
- `tests_passed` and `tests_failed`;
- `same_error_count`;
- `total_errors_seen`;
- `attempt_no`;
- `last_feedback_action`;
- `last_feedback_success`;
- `has_suspicious_region`;
- `code_lines`;
- `total_feedback_count_in_session`.

The trained classifier predicts one of four pedagogical actions:
- `CODE_HIGHLIGHT`;
- `CONCEPTUAL_HINT`;
- `GUIDING_QUESTION`;
- `NO_FEEDBACK`.

This design was selected because it keeps code analysis deterministic and interpretable, while allowing data-driven improvement of action selection.

## 10. Selected Tools and Technologies

### Backend
- Java 21
- Spring Boot
- Spring Security
- Spring WebSocket / STOMP
- JPA / Hibernate
- Flyway
- PostgreSQL

### Frontend
- Vue 3
- Vite
- Pinia
- Monaco-based code editor integration

### ML and experimentation
- Python
- pandas
- scikit-learn
- FastAPI
- joblib

### External services
- Gemini / OpenAI APIs for feedback realization

## 11. Deliverables

The expected deliverables of the project are:
- a working full-stack virtual mentor platform;
- a rule-based pedagogical policy baseline;
- an ML-based policy selector prototype;
- interaction logging and dataset export pipeline;
- evaluation results on latency and policy behavior;
- thesis documentation and article materials.

## 12. Advanced QA and Validation Strategy

Because Socrates AI is both an educational system and a software artifact with real-time constraints, quality assurance must cover not only correctness of code, but also runtime behavior, service integration, and pedagogical consistency. The QA strategy therefore combines functional testing, integration testing, performance evaluation, and artifact validation.

### 12.1 QA objectives

The quality assurance process must verify that:
- the system produces stable analyzer outputs for valid and invalid code inputs;
- the mentoring workflow consistently returns a valid pedagogical action;
- the frontend, backend, and ML service interact correctly;
- the system remains usable under real-time response constraints;
- fallback behavior works when external services fail;
- logs and exported datasets are complete and structurally valid.

### 12.2 QA scope by subsystem

#### Analyzer QA
- validate request parsing and response DTO correctness;
- test syntax-error detection and heuristic signal generation;
- verify suspicious-region and severity fields for representative code cases.

#### Policy QA
- verify rule-based action selection for known feature combinations;
- validate ML predictor input schema and output class mapping;
- confirm fallback from ML mode to rule mode when predictor is unavailable or fails.

#### Feedback QA
- verify template generation for each `FeedbackAction`;
- test external provider integration with Gemini/OpenAI;
- ensure generated responses remain concise and pedagogically constrained.

#### Interaction log QA
- verify that each mentoring call creates a complete interaction record;
- test update flow for outcome labels such as whether feedback helped;
- validate CSV export for dataset construction.

#### Frontend QA
- verify API integration and authentication flow;
- validate WebSocket reconnection and real-time feedback updates;
- confirm that the feedback panel correctly shows action, source, and resolution controls.

### 12.3 Test levels

The project should apply the following testing levels.

#### Unit testing
Used for:
- analyzer engines;
- policy selection rules;
- DTO and mapper logic;
- utility methods.

#### Integration testing
Used for:
- controller-to-service flow;
- database persistence;
- security and JWT-protected endpoints;
- ML service HTTP integration.

#### System testing
Used for:
- complete mentoring workflow from code submission to feedback display;
- frontend/backend interoperability;
- WebSocket-assisted real-time mentoring behavior.

#### Experimental testing
Used for:
- latency measurement;
- event-level action distribution analysis;
- comparison of rule-based and ML-based policy operation.

### 12.4 Quality metrics

The following quality indicators are appropriate for Socrates AI:
- API correctness and valid response structure;
- successful persistence of interaction logs;
- end-to-end response latency;
- policy selector availability and fallback reliability;
- frontend responsiveness and session continuity;
- completeness and consistency of exported training data.

For the ML extension, additional metrics include:
- accuracy;
- macro F1-score;
- class distribution coverage;
- predictor availability at runtime.

### 12.5 Quality gates

The project should use the following quality gates before accepting a phase as complete:
- the module compiles and integrates without breaking existing functionality;
- the exposed endpoint returns valid structured responses;
- interaction data is stored correctly;
- known failure cases are handled gracefully;
- the user-facing flow remains operational from frontend to backend;
- for ML mode, prediction service failure does not break the mentor pipeline.

### 12.6 Validation approach

Validation is carried out in two ways:
- **technical validation**, which verifies that the artifact works correctly and reliably;
- **research validation**, which verifies that the artifact addresses the target problem in a measurable way.

Technical validation focuses on software behavior, while research validation focuses on latency, feedback behavior, and feasibility of pedagogical intervention.

## 13. Implementation Task Breakdown

To operationalize the development plan, the following implementation tasks are required.

### Task Group 1. Architecture and backend core
- define module boundaries between analyzer, mentor, policy, feedback, interaction, and session layers;
- finalize DTO contracts for mentoring and analyzer requests/responses;
- ensure configuration support for `rule` and `ml` modes;
- maintain clean separation between analysis and policy logic.

### Task Group 2. Analyzer and signal extraction
- implement syntax and heuristic analysis engines;
- normalize analyzer outputs into a stable feature interface;
- support extraction of error type, severity, compile result, and suspicious region.

### Task Group 3. Student context and session tracking
- implement session lookup and lifecycle management;
- track attempt number and previous feedback action;
- compute repeated-error and total-error indicators for policy input.

### Task Group 4. Policy layer
- implement and verify the rule-based baseline policy;
- implement policy guards to prevent unsafe or inconsistent actions;
- integrate ML-based policy selection through a separate prediction service.

### Task Group 5. Feedback generation
- implement template-based feedback generation;
- integrate LLM providers for phrasing support;
- ensure the final message respects pedagogical constraints and does not expose full solutions.

### Task Group 6. Frontend integration
- implement task-solving UI and feedback display panel;
- connect REST and WebSocket services;
- support authentication and protected views;
- display policy outputs clearly to the user.

### Task Group 7. Logging, data export, and ML
- persist complete interaction logs;
- export datasets from stored interaction data;
- train and serialize the policy model;
- deploy the inference service and connect it to the backend.

### Task Group 8. QA and evaluation
- implement or document unit and integration tests;
- test real-time workflow end to end;
- measure runtime latency and stability;
- document system limitations and experimental constraints.

## 14. Risks and Mitigation

### Risk 1. Lack of authentic pedagogical labels
Mitigation:
- use rule-based policy as baseline;
- use weakly supervised or synthetic datasets for initial ML experiments;
- document limitations explicitly.

### Risk 2. Overclaiming ML effectiveness
Mitigation:
- position the ML component as a preliminary policy selector;
- separate feasibility claims from educational-effectiveness claims.

### Risk 3. Integration complexity across frontend, backend, and ML services
Mitigation:
- maintain clean service boundaries;
- keep ML as a separate service with a narrow API contract;
- preserve rule-based fallback mode.

### Risk 4. Latency problems in real-time usage
Mitigation:
- monitor latency in interaction logs;
- keep analysis lightweight;
- use bounded response pipeline design.

## 15. Acceptance Criteria

The project can be considered complete for the purposes of this assignment if the following criteria are met:
- the frontend can send code to the backend and receive feedback;
- the backend completes the full mentor workflow without manual intervention;
- the analyzer returns structured signals suitable for policy selection;
- the system can operate with a rule-based selector and optionally with an ML-based selector;
- interaction logs are stored and exportable for evaluation;
- the ML service can be invoked through a stable API contract;
- the system behavior and evaluation methodology are documented clearly.

## 16. Timeline Overview

A practical timeline for the project is:

| Phase | Main Work | Expected Result |
|---|---|---|
| 1 | Requirements and research review | Problem definition and system scope |
| 2 | Backend architecture and analyzer | Baseline mentor backend |
| 3 | Frontend and real-time flow | End-to-end student interaction |
| 4 | Feedback generation integration | Usable mentor responses |
| 5 | ML policy training and service integration | Learned policy prototype |
| 6 | Evaluation, documentation, thesis writing | Final report and article support |

## 17. Expected Outcome

The expected outcome is not simply a chatbot for programming help, but a structured virtual mentor system with explicit educational decision-making. The project is expected to demonstrate that a bounded-latency architecture combining analysis, student context, pedagogical policy, and feedback realization is technically feasible and suitable as a foundation for further educational evaluation.

## 18. Conclusion

The selected methodology for Socrates AI combines Design Science Research, incremental development, advanced QA, and experimental evaluation. This combination is appropriate because the project is both an engineering artifact and a research contribution. The development plan supports gradual implementation from a rule-based baseline toward an ML-extended virtual mentor, while preserving clarity, modularity, and evaluability.
