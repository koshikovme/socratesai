# SocratesAI Video Presentation Outline

## Slide 1 - Title

**Title:** SocratesAI Final MVP  
**Subtitle:** Real-time programming mentor for introductory Java students

**What to say**

> This is SocratesAI, my final MVP for a real-time programming mentor aimed at first-year Java students.

---

## Slide 2 - What I Built And For Whom

**Slide content**

- Product: real-time mentor inside the coding workflow
- User: first-year CS / CS1 Java student
- Problem: students get stuck on small syntax and logic mistakes and do not get timely individual help
- Core loop: write code -> get bounded feedback -> mark whether it helped

**What to say**

> I built a real-time mentor that stays inside the solving workflow. It is for first-year students learning Java, especially the student who understands the task in general but gets blocked by a small syntax or logic mistake. The core loop is simple: the student writes code, gets a bounded mentor response, and then marks whether the feedback helped.

---

## Slide 3 - One Decision I Stand Behind / One I Would Reverse

**Slide content**

- Decision I stand behind:
  Separate policy action selection from text generation
- Why:
  More control, safer educational behavior, easier testing
- Decision I would reverse:
  Trying to think too broadly about the analyzer too early
- Why:
  The current analyzer is still narrow, so earlier focus would have been better

**What to say**

> The decision I stand behind is separating pedagogical action selection from text generation. That made the mentor easier to control, safer for learning, and easier to test. The decision I would reverse is trying to think too broadly about the analyzer too early. If I started again, I would focus first on a smaller set of repeatable CS1 error cases.

---

## Slide 4 - Did The Hypothesis Hold?

**Slide content**

- Hypothesis held partially
- Evidence:
  - 570 event-level interactions
  - 740 ms mean latency
  - 800 ms P95 latency
  - 70.0% event-level resolution
- Not validated yet:
  7-day repeat usage across distinct tasks

**What to say**

> The hypothesis held partially. The technical part worked: the bounded mentor loop is real and fast enough to stay inside the workflow. In pilot logs, I recorded 570 event-level interactions, 740 milliseconds mean latency, 800 milliseconds P95 latency, and a 70 percent event-level resolution rate. What I still have not validated is retention, especially whether students return on a second task within the same week.

---

## Slide 5 - Next Step

**Slide content**

- Next step: small classroom pilot
- Goal:
  - test repeat usage
  - test usefulness across repeated tasks
  - validate partial NFRs under real usage

**What to say**

> My next concrete step is a small classroom pilot with repeated weekly tasks. That is the next real test, because it will show whether students actually come back, whether the feedback remains useful across more than one task, and whether the current partial performance and privacy requirements hold under real use.

---

## Recording Notes

- Keep the presentation segment to about `2-3 minutes`.
- Use the metrics only once and say them clearly.
- Do not repeat architecture details from the report unless they support one of the four required presentation points.
- If two people are presenting, split Slides 2-3 and Slides 4-5 between speakers.
