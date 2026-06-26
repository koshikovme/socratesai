# Assignment 4: Product Thinking & MVP Development

**Project:** SocratesAI  
**Prototype location:** `src/main/java/com/masters/socratesai` and `socratesai-frontend`

This assignment is based on my project **SocratesAI**, which is a real-time programming mentor for introductory programming students. The product idea is not "AI explains code" in the general sense. The narrower idea is more specific: when a beginner gets stuck on a small coding mistake, the system should respond inside the solving flow with a limited nudge, not a full answer.

In the current prototype, the core workflow already exists end to end. A student logs in, opens a task, writes code in the solving view, and then either presses **Ask Mentor** or keeps typing with realtime feedback enabled. The backend analyzes the code, selects a feedback action, returns a short response, and stores the interaction result for later review. That concrete workflow is what I use in the report below.

## Part 1 - MVP Definition

### 1.1 MVP Hypothesis

| Field | My answer |
|---|---|
| **The problem I am solving** | When I am a first-year programming student and I get stuck on a small Java mistake during a lab, I need a quick hint inside the editor so I can recover without waiting for a TA or pasting my whole solution into ChatGPT. |
| **My riskiest assumption** | Students will actually find **short, policy-limited hints** useful enough to keep using them. If they either ignore the hints or prefer a full answer from a general chatbot, then the product has no real value. |
| **My MVP hypothesis** | **If I build a real-time mentor that analyzes beginner Java code and responds with one of four actions - code highlight, conceptual hint, guiding question, or no feedback - I believe first-year CS students will use it during problem solving because bounded help inside the coding workflow is more useful than either silence or an unrestricted AI answer.** |
| **What the MVP is NOT** | It is **not** a full tutoring platform, **not** a code execution sandbox, **not** a grading system, **not** an open-ended chatbot, **not** multi-language support, and **not** a teacher analytics dashboard. I intentionally left those out because they make the system bigger without answering my main question. |
| **How I will test it** | I only need one working loop: student opens a task, writes code, sends code to the mentor, gets a bounded response, and marks whether the feedback helped. In my implementation this happens through `TaskSolveView.vue`, `POST /api/mentor/analyze-feedback`, the WebSocket path `/app/code.update`, the policy layer, and the interaction log endpoint for feedback outcome. |

The important part for me is that I did **not** define the MVP as "a simple version of the final platform." I defined it as the smallest version that can answer the real risk: do students get value from restricted, real-time mentoring inside the solving flow?

### 1.2 Product-Market Fit Signal

| Question | My answer |
|---|---|
| **Who is my primary user?** | My primary user is a **first-year computer science student taking an introductory Java programming course**, especially the student who understands the task in general but gets blocked by syntax mistakes, loop boundaries, or simple logic errors during labs and homework practice. |
| **What retention signal will I track?** | My main PMF signal is **7-day repeat mentor usage across distinct tasks**. More concretely: out of students who use the mentor on one task, how many come back within 7 days and use it again on a second task? I care more about that than signups, because returning to the tool during another solving session is much stronger evidence of value. A secondary signal is whether they keep marking feedback as helpful instead of abandoning the mentor after one try. |
| **Distribution risk** | The obvious distribution risk is that **LeetCode, HackerRank, CodeSignal, or even GitHub Copilot inside VS Code** could add an "explain my bug" or "hint me" button directly where users already code. If SocratesAI were just another generic AI helper, that would be a serious threat and probably kill the product. The reason I think it does not completely kill my idea is that my angle is narrower: classroom-safe feedback, teacher-controlled intervention style, session logging, and support for CS1 pedagogy rather than general productivity. Still, this is a real Kaspi-style risk. If one of those platforms adds policy-controlled educational hints for beginner courses, I would need a stronger moat than just the AI layer itself. |

I do **not** claim PMF yet. Right now I only have prototype evidence that the loop is technically viable and that some feedback gets used. PMF would mean students return without being forced to and prefer this workflow enough to make it part of how they practice.

### 1.3 Product Debt Awareness

| Debt risk | How it could appear in SocratesAI | How I will avoid it |
|---|---|---|
| **Feature creep** | It is very easy to keep adding attractive extras: full AI chat, teacher analytics, code execution, plagiarism checks, class dashboards, multi-language support, rankings, and assignment authoring. I already have side features like task management and LeetCode fetching, so this risk is real. | I will keep the product centered on one core question: does real-time bounded feedback help novice programmers recover faster? If a feature does not improve that loop, it should wait. |
| **Wrong early-adopter assumption** | A strong LeetCode user is not the same as a struggling CS1 student. If I validate the product only with confident students who already know how to debug, I may conclude the hints are "good enough" when they are actually too abstract for beginners. | I need to test mainly with the group I am targeting: first-year students in intro programming, not only advanced friends or people who already grind coding platforms. |
| **North star metric misalignment** | If I optimize for the number of mentor requests, total session time, or "engagement," I could accidentally reward confusion. A student asking for 15 hints is not necessarily a success story. | My better metrics are repeat task usage, resolved-after-feedback, time-to-fix, and whether students return to a second task. Those are closer to actual user value than raw activity volume. |

This section matters because product debt in my case would not come from bad code first. It would come from building the wrong shape of product around the mentor idea.

## Part 2 - Ethics & Privacy

### 2.1 Privacy by Design

| Principle | How SocratesAI applies it |
|---|---|
| **Data minimisation** | For the user profile, the system only needs basic account data such as email, hashed password, full name, and role. University and group are optional profile fields, not required for the mentor loop itself. For mentor interactions, the backend stores analyzer metadata, feedback action, and a **code hash** in `InteractionLog` instead of persisting the raw code snapshot in the interaction log table. That is a deliberate minimisation decision because I need to study the feedback loop, not archive every line a student typed forever. |
| **Purpose limitation** | Interaction data should be used for delivering feedback, improving the policy, and evaluating whether the mentor helped. It should **not** silently become grading evidence, behavioural profiling, or marketing data. One thing I would tighten in the current prototype is dataset export: if I export interaction logs for ML retraining or research, student identity should be pseudonymised first and that use should be clearly separated from ordinary product use. |
| **Default = private** | The application already treats mentor activity as private by default. Protected REST routes require JWT auth, user profile endpoints are under `/api/users/me`, and realtime mentor responses are sent through a **user queue** (`/user/queue/feedback`) rather than a public topic. That means one student's mentor feedback is not broadcast to everyone else. |
| **Right to erasure (GDPR Art. 17)** | This is the weakest part of my current prototype and I want to be honest about that. I do not yet have a full deletion workflow. For the real product, I would add a delete account flow that removes the user profile, active student task sessions, interaction logs, cached frontend identity data, and any future training exports tied to that user. Backup retention would also need a defined deletion window rather than keeping old copies indefinitely. |

The main privacy lesson for me is that "I only need it for research later" is not a good excuse to keep extra student data. If I do not need it for the core mentor loop, I should not collect or store it by default.

### 2.2 Ethical Risk Analysis

| Risk | EAD principle | Who is affected | My mitigation |
|---|---|---|---|
| **Risk 1: SocratesAI could become a shortcut for cheating instead of a learning tool** | **Awareness of Misuse** | Students, instructors, and the integrity of the course | This is the most obvious ethical risk in my product because the mentor works directly inside the coding workflow. If it starts giving full fixes or near-complete answers, students may use it to finish tasks without actually understanding them. To reduce that risk, I designed the system around bounded help rather than unrestricted generation. The mentor can only choose from four feedback actions, and the prompt explicitly tells the model not to provide a full solution or corrected code. In a stricter course mode, I would narrow this even further so that graded tasks only allow highlights or guiding questions. For me, the ethical line is simple: if I would feel the need to hide a more permissive “answer mode” from instructors, then that feature should not exist. |
| **Risk 2: students may trust feedback that sounds confident even when it is incomplete or wrong** | **Transparency** | Mainly beginner students, because they often do not yet know when to question the tool | This risk matters because novice programmers can easily mistake confident wording for correct guidance. Even a short hint can send them in the wrong direction if the system sounds certain when it is actually unsure. My current mitigation is to keep responses short, connect them to visible analyzer signals such as the error type and suspicious region, and let students mark whether the feedback helped. The interface already shows the action type, error type, region, and whether the feedback came from the manual or realtime flow. In a stronger version of the product, I would also show whether the response came from a template or Gemini and make low-confidence cases much more visible. If the product only appears smart by hiding uncertainty, then it is not being honest with students. |

I chose these two risks because they come from the actual behaviour of SocratesAI, not from generic software risks. In my case, the main ethical challenge is not only protecting data. It is making sure the product supports learning without quietly replacing it.

## Part 3 - MVP Prototype

### 3.1 What I built

My prototype already covers one full end-to-end workflow that directly tests the hypothesis.

**Core workflow**

1. The student logs in and opens a task from the dashboard.
2. The student writes Java code in the solving view.
3. The code goes to the backend either through **Ask Mentor** (`POST /api/mentor/analyze-feedback`) or through the realtime WebSocket path (`/app/code.update`).
4. The backend creates or reuses a `StudentTaskSession`, analyzes the code, builds a small student context, selects a feedback action, generates short feedback, and returns it to the UI.
5. The student can mark the feedback as **It helped** or **Still stuck**, which is stored through `/api/interactions/{interactionId}/result`.

**User interface**

The prototype has more than the minimum two screens:

- login/register flow
- dashboard with tasks
- task solving workspace with Monaco editor, mentor controls, and feedback panel

**Data flow**

The data changes based on student input. This is not a static mockup. Different code produces different analyzer results, different policy actions, and different feedback messages. For example, the current heuristic analyzer distinguishes at least these cases:

- empty editor
- syntax failure for Java when the code is missing a semicolon-style ending
- off-by-one style loop suspicion
- wrong condition / possible infinite loop suspicion

It is still a narrow heuristic engine, but it is a real processing flow, not fake buttons.

### 3.2 Hypothesis Test Report

| Question | My answer |
|---|---|
| **Did the prototype test my MVP hypothesis?** | **Yes, partially.** It tested whether I can deliver bounded feedback fast enough to stay inside the coding workflow. In my pilot logs I recorded **570 event-level interactions**, with a mean end-to-end latency of **740 ms** and a P95 of **800 ms**. I also observed an aggregate event-level resolution rate of **70.0%**. That does not prove learning gains or PMF, but it does show that the mentoring loop itself works in real time and is not just a conceptual idea. |
| **What did I learn that I did not know before building?** | The biggest thing I learned is that the hard problem is **not** "generate an explanation." The harder product problem is deciding **how much help** to give. Once I implemented the policy layer, it became obvious that beginners do not always need the same kind of feedback. I also learned that my analyzer is much narrower than I first imagined. Right now it is credible for a focused CS1 debugging assistant, but it is nowhere near a general-purpose code tutor yet. |
| **What product debt did I introduce, and why?** | I introduced several kinds of deliberate debt. First, the analyzer still relies on simple heuristics and string patterns, which is fast for prototyping but shallow. Second, I now have both REST and realtime flows hitting the same mentor workflow, which is useful for testing but adds duplication. Third, some prototype configuration is still too local and not production-ready. Fourth, the right-to-erasure flow is not complete yet. I accepted this debt because my immediate goal was validating the mentoring loop, not polishing the whole platform. |
| **What is the next hypothesis to test?** | The next riskiest assumption is about **retention**, not latency. My next hypothesis is: **If first-year students find the feedback useful on one task, they will come back and use SocratesAI again on a second task within the same week without being forced to do so by the instructor.** To test that, I need a small real classroom pilot with repeated tasks, not just isolated prototype sessions. |

## Part 4 - Reading Connection

| Source | Key idea I applied | Where it appears in my work |
|---|---|---|
| **The Lean Startup - Eric Ries (2011)** | An MVP is not a smaller copy of the final product. It is the smallest version that lets you **build, measure, and learn** from real user behavior. | This idea shaped how I scoped SocratesAI. I did not try to build a full tutoring platform. I focused on one working loop: the student writes code, gets feedback, and then marks whether the feedback **helped** or they are **still stuck**. That last step matters because it gives me something measurable. Without it, I would only be building features, not learning whether the product is actually useful. |

That reading changed how I thought about the project. My first instinct was to keep adding features until the system looked complete. Ries pushed me to think differently: what is the smallest version I can build that tells me whether students really find this kind of feedback useful?

## Final Reflection

What I like about SocratesAI as an MVP is that it is focused. The real product idea is not "AI for education" in the abstract. It is a more disciplined claim: a beginner programmer can benefit from **small, timely, policy-controlled help** during coding, and that help should stay inside the task flow.

What I still do **not** have is product-market fit. I have a working prototype, some encouraging pilot evidence, and a clearer understanding of the ethical and product risks. That is useful, but it is still early. The next stage is not adding ten more features. The next stage is seeing whether real students come back to the product when nobody makes them.
