# Project memory — Larder / Cook Assistance

## How this repository is built
- Implemented with the `blueprint-service-implementation` pipeline, one increment at a time
  (increment 0 = walking skeleton US-CA-03; next: increment 1 — Board: Help with a preparation
  step). The ledger is `docs/cooking-assistance/implementation-log.md`, with the journals in
  `docs/cooking-assistance/ledger/`; the planned list is in the ledger.
- Method: EXACT Coding (Predictive TDD), vendored in `.claude/skills/` at 88fb3f9, autonomy
  **full-hitl**: stop after the test list, after each Red, after each refactor (keep or undo)
  and after every prediction mismatch. One behavior per activation; a prediction before every
  check; a trial for every test green on activation and every new gate.
- Refactoring runs in an isolated `refactor` agent per group; tests are snapshotted before and
  must be unchanged after.
- Reviews (reverse check, Fagan inspection, OWASP review) run in separate read-only agents.

## Commands
- `./mvnw test` fast suite; `./mvnw verify` everything (generation, Spotless, Error Prone, fast
  and slow suites on Testcontainers, Spectral); work offline with `mvn -o`.
- Intent coverage: `python3 tools/coverage/intent_coverage.py docs/cooking-assistance/coverage/inventory-inc0.yaml .`

## Layout
- `cooking-assistance/`: hexagonal module (`domain`, `application`, `adapter.in.rest`,
  `adapter.in.messaging`, `adapter.out.persistence`, `adapter.out.messaging`; ArchUnit, ADR0008).
- `larder-app/`: the host; configuration only from `LARDER_*` environment variables (README).
- Generated code (OpenAPI generator, Modelina) lives in `target/generated-sources` and is never
  edited (ADR0014); a change goes to the spec (step 4) with a CHANGELOG entry and a version bump.

## Rules
- Never push, open a pull request or merge without asking; the user merges.
- Commit only after the user approves; Conventional Commits; messages end with the
  Co-Authored-By line.
- The pre-commit hooks stay uninstalled (the user's choice); `verify` and CI are the gates.
- Never change a spec, ADR or Example Mapping to fit the code without the user's approval; a
  drift is either a document correction (owning step) or a defect (test-first).
