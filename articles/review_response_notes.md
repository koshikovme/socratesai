# Reviewer-Oriented Revision Notes

## Reviewer #1

### Comment
The novelty should be highlighted more clearly against existing LLM assistants and ITS. The paper should better explain how the approach improves over prior work, add broader evaluation if possible, and clarify limitations.

### Revision made
- Repositioned the paper as a systems/prototype contribution rather than a broad learning-effectiveness claim.
- Added an explicit positioning table comparing the proposed system with ITS and LLM-based assistants.
- Strengthened the architecture section to separate analyzer signals, lightweight student state, policy selection, and realization.
- Clarified that the main contribution is the bounded-latency virtual mentor pipeline and explicit pedagogical policy layer.
- Expanded the limitations section and made the lack of longitudinal/classroom-scale evidence explicit.

## Reviewer #2

### Comment
The paper lacks strong technical depth, has weak dataset description, limited experimental scale, no benchmark comparison, and no statistically validated learning outcomes.

### Revision made
- Added a formal policy view and implementation-oriented architecture description.
- Added explicit explanation of the feature interface used for policy selection.
- Clarified that the evaluation artifact is a pilot event-level interaction log with N=60 events.
- Narrowed evaluation claims to prototype feasibility and event-level effectiveness trends.
- Explicitly stated that the paper does not yet claim benchmark superiority, inferential classroom evidence, or longitudinal learning gains.
- Framed baseline-vs-ML comparison and larger studies as future work.

## Editorial Changes

- Updated title to emphasize bounded latency and system implementation.
- Removed dependency on an external architecture image by replacing it with a LaTeX-native architecture diagram.
- Prepared a compile-friendly bibliography target name `references.bib`.
