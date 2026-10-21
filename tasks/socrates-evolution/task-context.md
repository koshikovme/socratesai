# Context & Requirements: SocratesAI Evolution to Contester Platform

## 1. Project Background & Objective
SocratesAI is currently a research prototype providing adaptive programming support for first-year students via real-time WebSocket feedback based on ML state classification and LLM-phrased hints.
**Objective:** Evolve this prototype into a full-scale "Contester" platform (similar to LeetCode/Codeforces) while preserving the core real-time AI/ML feedback loop.

### Feature Shift:
* **Before:** Fetched tasks from external LeetCode API.
* **After:** Teaching staff (Teachers/Admins) must be able to create coding tasks, group them into collections, and students must solve them inside the platform supporting multiple target programming languages.

---

## 2. Architecture & Tech Stack Boundaries
The system is built on Clean Architecture and strict modular boundaries:
* **Backend:** Java 21, Spring Boot 4, PostgreSQL, Flyway, WebSockets, JWT Auth.
* **Frontend:** Vue 3, Vite, Pinia, Monaco Editor.
* **ML Service:** Python 3.11+, FastAPI, scikit-learn, pandas.

---

## 3. Mandatory Development Rules & Best Practices

### Code Design & Structure:
1. **Design Principles:** Adhere strictly to **SOLID, DRY, KISS, YAGNI, and GRASP**. Write clean, readable, and highly maintainable code.
2. **Clean Architecture:** If rewriting or extending large modules (e.g., Code Sandbox or Teacher Dashboard API), build for high scalability, easy maintenance, and resilience.
3. **Encapsulation:** Deduplicate logic. Extract repeating routines into separate helper methods, classes, or shared modules.
4. **Naming Conventions:** Use clear, straightforward, and expressive names for all variables, functions, and classes.

### Efficiency & Performance Anti-Patterns:
1. **Memory & DB Load:** Do NOT write inefficient code that risks heavy execution loads or memory overflows in the database or server.
2. **Modern Structures:** Avoid deprecated or unnecessarily verbose structures. Use **Stream API** instead of manual loops where appropriate. Use Lombok annotations (`@AllArgsConstructor`, `@RequiredArgsConstructor`) instead of explicit verbose constructors unless specifically necessary.

### Testing & Git Rules:
1. **Testing Requirement:** Code must be fully covered by automated tests. Real live API/E2E testing will be conducted after exposing the local environment through auth tokens.
2. **Git Workflow:** Commit frequently on local git using correct semantic commit prefixes (`feat/`, `fix/`, `ref/`, `docs/`, `test/`). I allow you to perform pushing in git;

## 4. Required Output Format for Planning Step
The model must take this context and decompose the transition into concrete, sequential, actionable technical subtasks.
The output must be written as a markdown file at `/tasks/socrates-evolution/work-notes.md`.

The decomposition must explicitly split the system into:
1. **Database Schema Evolution:** Migration from LeetCode identifiers to native relational entities (`User`, `Role`, `Problem`, `Collection`, `Submission`) while keeping ML-feature logging fields (`interaction_logs`) intact.
2. **Execution Sandbox Module:** Safe compilation and runtime testing architecture for multiple languages.
3. **Teacher API Layer:** Secure endpoints for problems/collections CRUD.
4. **WebSocket & ML Real-time Sync:** How the existing WS pipeline maps code diffs to local database problem contexts instead of LeetCode API.
5. **CI/CD & Production Infrastructure:** Dockerizing the entire multi-service container mesh for production hosting (`backend`, `frontend`, `policy-api`, `postgres`).
