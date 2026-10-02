<!--
Sync Impact Report
------------------
Version change: n/a -> 1.0.0 (initial ratification)
Modified principles: none (initial version)
Added sections: Additional Constraints (Non-Invasion, Dependency Discipline),
  Development Workflow, Governance
Removed sections: none
Follow-up TODOs: none
-->

# simula-agent Constitution

## Core Principles

### I. Test-First (NON-NEGOTIABLE)
Test-Driven Development is mandatory. Tests are written before the implementation
they validate, in this strict order: tests written -> user approved -> tests fail
with a clear reason -> implementation added. The Red-Green-Refactor cycle is
strictly enforced and no production code may be merged without its accompanying
test written first.

### II. Coverage Standard
Every deliverable MUST achieve a line coverage and a branch coverage of at least
97%. Coverage is measured by an automated coverage tool against the code under
test; a merge or release is blocked when either metric falls below the threshold.
JVM bootstrap and bytecode-advice glue that cannot execute meaningfully under the
coverage agent MAY be excluded, but every exclusion MUST be declared in the build
file with a one-line justification next to it. Rationale: branch coverage prevents
the common failure of hitting every line while leaving conditional paths untested.

### III. English Code
All source code, identifiers, function names, variable names, string literals, and
file names MUST be written in English. No non-English terms may appear in code
unless they are domain-specific proper nouns that cannot be translated.
Rationale: English maximizes readability and collaboration across the team and
tooling.

### IV. English Comments
All comments, documentation strings, and inline annotations MUST be written in
English. Comments MUST explain the rationale and intent, not merely restate the
code. Rationale: comments form part of the durable documentation surface and must
be equally understandable by every contributor.

### V. Formatting
All code MUST conform to the project's automated formatter (fmt-maven-plugin with
google-java-format). Formatting is applied before a change is submitted, and any
formatted diff is enforced by the quality gate. Rationale: consistent formatting
removes review noise and keeps diffs focused on behavioral change.

### VI. Documentation
API documentation is mandatory for every package, every class, and every method
regardless of visibility, using Javadoc. The documentation MUST describe the
purpose and contract of the element, including parameters, return values, and
thrown exceptions where applicable, MUST be written in English, and MUST cite the
requirements the element participates in implementing (FR-### / SC-###).
Rationale: exhaustive API documentation makes APIs self-describing.

### VII. Named Literals (NON-NEGOTIABLE)
All string and numeric literals MUST be declared as named constants. Raw
literals (magic values) are forbidden in code. The integer literals `-1`, `0` and
`1` are the sole exceptions and MAY be written inline; every other literal MUST be
assigned to a named constant and referenced by that name. Rationale: named
constants make intent explicit and keep values traceable and single-sourced.

### VIII. Architecture Document
The project MUST maintain a current architecture document (`architecture.md`)
describing the system as implemented. The document MUST contain no historical
information and MUST be updated as part of any change that alters a component,
process, dependency, or decision. All structural diagrams MUST be Mermaid
diagrams inside fenced `mermaid` blocks. Rationale: the architecture document is
the durable, living reference for how the system is built today.

### IX. Instrumentation Contract Stability (NON-NEGOTIABLE)
The framework signatures the agent transforms (constructors and methods of the
simula framework) are a stability contract. Every transformation MUST degrade
softly: when a signature is absent or changed, the agent MUST log a diagnostic and
continue without instrumentation — it MUST NOT abort, crash, or alter the behavior
of the instrumented application. Compatibility MUST be proven by an integration
test that launches a real simula application with the agent attached.
Rationale: an instrumentation agent that can break its host application is a
defect, not a feature; soft degradation plus integration tests keep the host safe
across framework evolution.

## Additional Constraints

### Non-Invasion
The agent MUST NOT require any modification of the instrumented application, of
the simula framework source, or of their build artifacts. Instrumentation is
activated solely by the `-javaagent` JVM option.

### Dependency Discipline
The runtime classpath of the agent is limited to the shaded, relocated Byte Buddy
engine bundled in the agent jar. The simula framework is a `provided` compile-time
dependency for interface types only. No JMX, JFR, or other cross-process telemetry
mechanism may be introduced without a constitution amendment.

### Quality Gates & Compliance
- Coverage MUST be measured and reported for both lines and branches on every
  change; the 97% threshold applies to both metrics independently.
- A change that fails any gate (coverage, formatting, or linting) MUST NOT be
  merged or deployed until the failure is corrected and the gate passes.
- Test suites MUST be runnable with a single command and MUST be deterministic.
- Advice code injected into transformed classes MUST stay allocation-light and
  MUST NOT block; hot-path overhead MUST be guarded by a micro-benchmark budget.

## Development Workflow

### Test-First Workflow
- Start every feature or fix by authoring the tests that define the expected
  behavior, following the test-first order described in Principle I.
- After the user approves the tests, confirm they fail for the intended reason
  before writing any implementation (Red).
- Implement the minimal code required to make the tests pass (Green), then
  refactor while keeping the suite green (Refactor).
- Run the full test suite, verify the 97% line and branch coverage thresholds, and
  apply the formatter before submitting the change for review.

## Governance
This constitution supersedes all other practices, guidance, and ad-hoc decisions
within this project. Any deviation from these principles MUST be documented,
justified, and approved before it is accepted.

- Amendments: proposed changes MUST be documented, approved by the project owner,
  and recorded here with an updated version and amendment date.
- Versioning: the version is bumped per semantic versioning rules. A MAJOR bump
  indicates a backward-incompatible principle change, a MINOR bump adds a
  principle or materially expands guidance, and a PATCH bump records
  clarifications or non-semantic refinements.
- Compliance review: every pull request and release MUST be checked against these
  principles; coverage, formatting, documentation, and language standards are
  enforced automatically where tooling allows.

**Version**: 1.0.0 | **Ratified**: 2026-10-01 | **Last Amended**: 2026-10-01
