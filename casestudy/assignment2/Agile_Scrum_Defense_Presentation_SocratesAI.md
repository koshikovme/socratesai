# Agile Scrum Defense Presentation - SocratesAI

**Subject:** Agile Project Management  
**Supervisor:** Bushuyev Denys  
**Project:** SocratesAI

## Presentation Format

- Target length: `5-7 minutes`
- Recommended slides: `8`
- Style: short bullets on slides, explanation in speech

## Slide 1 - Title

**Slide title**  
SocratesAI: Agile Scrum Project Defense

**Slide content**

- Real-time programming mentor for CS1 students
- Built with Agile Scrum
- Focus: bounded feedback during coding

**What to say**

> My project is SocratesAI, a real-time programming mentor for introductory programming students. I organized the project using Agile Scrum because the core idea was clear, but many implementation details were still uncertain and needed to be developed incrementally.

---

## Slide 2 - Project Goals

**Slide title**  
1. Project Goals

**Slide content**

- Help first-year Java students recover from small coding mistakes in real time
- Keep feedback short, safe, and educational rather than solution-generating
- Build a working end-to-end MVP that can be tested and improved with real interaction data

**What to say**

> I defined three main goals. First, I wanted to help first-year Java students when they get stuck on small syntax or logic mistakes. Second, I wanted the feedback to stay bounded, so the system guides learning instead of replacing it. Third, I wanted to build a real MVP, not only a concept, so that student interactions could be logged and used for later improvement.

---

## Slide 3 - Stakeholders

**Slide title**  
2. Stakeholders

**Slide content**

| Stakeholder | Interest in the project | Influence |
|---|---|---|
| First-year programming students | Need fast and understandable help while solving tasks | High |
| Course instructors / TAs | Want fewer repetitive debugging questions and more consistent support | High |
| Supervisor: Denys Bushuyev | Evaluates project quality, methodology, and delivery | High |
| Developer: Alimzhan Koshikov | Designs, implements, tests, and prioritizes the MVP | High |
| University / course environment | Needs a tool that is ethical, usable, and realistic for education | Medium |

**What to say**

> The primary stakeholder is the first-year student, because the product is built for that user directly. Instructors and teaching assistants are also important, because the tool should reduce repetitive support work without encouraging cheating. My supervisor is a key stakeholder because the project must satisfy both product and methodology expectations. Since this is a solo project, I also acted as the main delivery stakeholder myself.

---

## Slide 4 - Scrum Roles

**Slide title**  
5. Roles

**Slide content**

| Scrum role | Person | Responsibility in this project |
|---|---|---|
| Product Owner | Alimzhan Koshikov | Defined scope, prioritized backlog, kept focus on the MVP hypothesis |
| Scrum Master | Alimzhan Koshikov | Planned sprints, tracked blockers, protected scope from feature creep |
| Development Team | Alimzhan Koshikov | Implemented backend, frontend, ML service, tests, and documentation |
| External stakeholder | Denys Bushuyev | Provided academic direction and evaluation context |

**What to say**

> Because this was a solo academic project, I combined the Product Owner, Scrum Master, and Developer roles. I still kept the role logic separate: as Product Owner I prioritized value, as Scrum Master I managed the sprint structure, and as Developer I implemented the system. That made the Scrum model lighter, but still meaningful.

---

## Slide 5 - Product Backlog

**Slide title**  
3. Product Backlog

**Slide content**

| Priority | Product Backlog Item | Business value |
|---|---|---|
| P1 | User authentication and role handling | Secure access for students and teachers |
| P1 | Task dashboard and task retrieval | Gives students tasks to solve |
| P1 | Code editor workspace | Main environment for solving problems |
| P1 | Realtime code monitoring with WebSocket | Enables in-flow help during coding |
| P1 | Heuristic analyzer for syntax and beginner logic errors | Detects the problems worth reacting to |
| P1 | Mentor policy layer with bounded actions | Controls pedagogical behavior |
| P1 | Feedbak generation layer | Produces hint, highlight, or question |
| P1 | Interaction logging and feedback outcome capture | Supports measurement and future improvement |
| P2 | Student session and context tracking | Makes feedback adaptive across attempts |
| P2 | ML policy service and dataset export | Extends the rule-based baseline |
| P3 | Broader tutor features such as open chat, analytics, multi-language support | Valuable later, but not required for MVP validation |

**What to say**

> I prioritized the backlog around the core mentoring loop. The highest-priority items were the pieces required for one complete student workflow: login, open a task, write code, get bounded feedback, and save the interaction result. Lower-priority items, like richer analytics or broader tutor features, were intentionally left behind the MVP boundary.

---

## Slide 6 - Sprint Backlog

**Slide title**  
4. Sprint Backlog

**Slide content**

| Sprint | Sprint goal | Selected backlog items | Output |
|---|---|---|---|
| Sprint 1 | Define scope and architecture | Requirements refinement, module boundaries, data flow | Clear MVP scope and architecture |
| Sprint 2 | Build the backend mentoring core | Analyzer, DTOs, sessions, interaction logging, rule-based policy | Working backend prototype |
| Sprint 3 | Connect the student workflow | Frontend solve view, REST flow, WebSocket updates | End-to-end student flow |
| Sprint 4 | Improve pedagogical feedback | Feedback templates, Gemini phrasing, bounded response rules | Usable feedback layer |
| Sprint 5 | Add ML extension | Dataset preparation, preliminary classifier, prediction API | Optional ML policy path |
| Sprint 6 | Stabilize and verify | Tests, validation, latency checks, fixes, report preparation | Final deliverable |

**What to say**

> I used six short sprints. Each sprint had one clear goal and one visible output. That helped me avoid building everything at once. For example, Sprint 2 focused only on the backend core, Sprint 3 connected the student workflow, Sprint 5 added the optional ML service, and Sprint 6 was dedicated to testing, validation, and final delivery.

---

## Slide 7 - Rituals

**Slide title**  
6. Rituals

**Slide content**

- **Sprint Planning:** choose sprint goal and backlog items
- **Daily Scrum:** short self-check on progress, next task, blocker
- **Sprint Review:** demo the working increment against requirements
- **Sprint Retrospective:** reflect on what worked and what should change

**How I applied them**

- Planning was done at the start of each sprint using the six-sprint roadmap
- Daily Scrum was lightweight because I was working solo
- Reviews were tied to working increments and milestone checks
- Retrospectives focused on scope discipline, testing gaps, and backlog adjustment

**What to say**

> I used the standard Scrum rituals, but in a lightweight form because this was a solo project. Sprint planning was important because it forced me to define one goal at a time. Daily Scrum became a short self-check. Sprint review happened whenever I had a working increment to compare against the requirements. Retrospectives were especially useful for recognizing when I was drifting into feature creep or postponing non-functional work too much.

---

## Slide 8 - Why Scrum Was The Right Choice

**Slide title**  
Why Agile Scrum Fit This Project

**Slide content**

- Requirements were stable at the idea level, but flexible at the implementation level
- The project had research uncertainty: analyzer depth, policy behavior, ML usefulness
- Working increments were more valuable than one final big delivery
- Scrum helped keep the MVP focused on real validation, not feature accumulation

**What to say**

> Scrum was the right choice because the project did not have fixed implementation details from the beginning. I knew the product problem, but not the final shape of the analyzer, the policy layer, or the ML extension. Scrum let me build the system incrementally, test each part, and keep the product centered on the MVP hypothesis instead of turning it into a much larger platform too early.

---

## Defense Close

**Short closing statement**

> In summary, SocratesAI was managed as an Agile Scrum project with a clear product goal, defined stakeholders, a prioritized product backlog, six practical sprint backlogs, combined but distinct Scrum roles, and lightweight but real Scrum rituals. The main advantage of using Scrum here was that it let me validate the core mentoring loop step by step instead of trying to build a complete tutoring platform all at once.

## Optional Q&A Answers

**Why not Waterfall?**

> Because the product direction was clear, but the implementation details were uncertain. Waterfall would have made late changes more expensive.

**Why Scrum if you worked alone?**

> Because Scrum still gave me useful structure: prioritized backlog, sprint goals, incremental delivery, and reflection after each stage.

**What was the biggest Scrum benefit in this project?**

> It protected the MVP from feature creep and kept the work centered on one measurable loop: code, feedback, and outcome.
