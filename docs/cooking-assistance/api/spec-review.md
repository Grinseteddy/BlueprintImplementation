# Step 4 — Spec review: Cook Assistance OpenAPI + AsyncAPI (v1.0.0)

**Specs:** copied from `SamplesDddMeetAi/Chapter12/{OpenAPIs,AsyncAPIs}/ClaudeFable51/` to `docs/cooking-assistance/api/`.

## Lint (Spectral 6.15.0, house-style rulesets)

| Spec | Errors | Warnings |
|---|---|---|
| OpenAPI | 0 | 1 — tolerant-reader note missing in `info.description` |
| AsyncAPI | 0 | 49 — 43 payload properties without description · 3 channel addresses not kebab-case (AMQP dotted routing keys) · 2 messages without examples · tolerant-reader note |

## Reconciliation table

| Slice | User stories | Board element | Expected contract | Found | Status |
|---|---|---|---|---|---|
| B3-S01, B2-S01, B4-S01, B1-S01 | 03, 02, 04, 01a | command Request help <type> | OpenAPI POST | `POST /help-requests` (type field) | ok — one operation for 4 commands by `type`; see P3 |
| same | same | event Help requested | AsyncAPI publish | `HelpRequestCreated` on `cooking-assistance.help-request.created` | name drift: board "Help requested"; Grandma's spec consumes `HelpRequested` on exchange `cook-assistance`; Notification expects `help.requested` → **P10** |
| B3-S02 … B1-S02 | all | command Provide Help | POST + consumed message (Grandma Avatar) | `POST /helps`; consumes `HelpProvided` from its own exchange | **drift**: Help is an entity (D1) → P4; consumer loop (we consume what we publish) → **P10** |
| same | same | event Help provided | AsyncAPI publish | `HelpProvided` | ok (payload changes P6, P8) |
| B3-S03 | 03, 02, 04, 01c | command Cancel help request, event Help request cancelled | POST cancellation (+ note); publish | `DELETE /help-requests/{id}`, only while Open, no note, no event | **missing / conflicts** with D4 and INV-08 → **P1, P2** |
| B3-S04 | 03, 02, 04, 01b | command Accept help, event Help accepted | POST acceptance; publish | — | **missing** → **P1, P2** |
| B3-S05 | 03, 02, 04, 01b, 01c | command Reject help (+ note), event Help rejected | POST rejection; publish | — | **missing** → **P1, P2** |
| B3-S06 | 03, 02, 04, 01c | automation Time out, event Help request timed out | publish only | — | **missing** → **P2** |
| read models Help request <type> | all | GET | `GET /help-requests`, `/{id}` | ok; status enum lacks CLOSED / CANCELLED; no deadlines, notes or per-provider state → **P5** |
| read models Help <type> | all | GET | `GET /helps`, `/{id}` | ok after P4 |
| — | — | — | `PATCH /help-requests/{id}` (change any field, set status) | **no slice**; contradicts INV-02 E3 ("step not added") → **P3** |
| — | — | — | `PATCH /helps/{id}`, `DELETE /helps/{id}` | **no slice** → **P3** |
| INV-11 | 02 | one ingredient + substitute requirements | request field | `ingredients[]` (0..*) | **drift** → **P6** |
| INV-03, 20, 38 | 03, 04 | request photos (B4 only, ≤ 10); answer pictures ≤ 10 | fields | no request pictures; answer `images` unbounded | **missing** → **P6** |
| INV-36 | all | a Cook never answers their own request | description | "a cook can answer their own request - except for Chef" | **conflicts** with D3 → **P7** |
| INV-27, 29, 33 | 01a, 01b | Occasion, chosen Chef, Grandma Menu to review | request fields | — | **missing** (Q_EM_OccasionInHelpRequest) → **P9** |
| examples' rejections (ChefNotAllowed, OccasionTooClose, NotPremium …) | all | 400 / 403 / 409 / 422 with a code | error model | custom `Error {code, message}` | ok-ish → **P11** (Problem Details) |

## Proposed improvements

v1.0.0 has no consumers yet: nothing is implemented, and no other context's code reads these specs. "Breaking" below means a breaking change to the v1.0.0 document. Since nobody consumes it yet, I propose applying these changes **in v1.0.0 as a draft revision**, not as a new major version. You decide.

| # | Finding | Change | Breaking? |
|---|---|---|---|
| **P1** | Cancel / Accept / Reject are commands with no operation; `DELETE` withdraws only while Open, without a note | Task-based operations: `POST /help-requests/{id}/cancellation` (optional note) · `POST /help-requests/{id}/helps/{helpId}/acceptance` · `POST /help-requests/{id}/helps/{helpId}/rejection` (note required). Remove `DELETE /help-requests/{id}` | yes (removes DELETE) |
| **P2** | Lifecycle events missing in AsyncAPI | Publish `HelpRequestCancelled` (note), `HelpAccepted`, `HelpRejected` (note, new deadlines), `HelpRequestTimedOut` (suggested providers) on `cooking-assistance.help-request.{cancelled,timed-out}` and `cooking-assistance.help.{accepted,rejected}` | additive |
| **P3** | `PATCH /help-requests/{id}`, `PATCH /helps/{id}`, `DELETE /helps/{id}` have no slice and allow what the rules forbid (status by hand, adding steps) | Remove all three | yes |
| **P4** | Help is an entity of Help Request (D1) | `POST /help-requests/{id}/helps` (provide help) and `GET /help-requests/{id}/helps[/{helpId}]`; keep `GET /helps?helpProvider=…` as a cross-request query for providers; remove `POST /helps` | yes |
| **P5** | Read model lacks the lifecycle | Status enum `OPEN, ANSWERED, CLOSED, CANCELLED` (documented as open for growth); add `providers: [{providerType, deadline, state: ASKED/ANSWERED/TIMED_OUT}]`, `notes[]` (rejection and cancel notes), `previousNotes[]` (INV-07: same step / same dish; red card US-CA-03 Q1), `acceptedHelp` link | additive except the enum |
| **P6** | Request content per type does not match the maps | `ingredients[]` → `ingredient` (one uuid) + `substituteRequirements` (text, e.g. "vegetarian", "not available"); `pictures` (0..10 uri) on the request, allowed only for STEPS_TO_MITIGATE_CATASTROPHE; `images` on answers `maxItems: 10` | yes (ingredients) |
| **P7** | Own-request rule conflicts with D3 | Text: "a cook never answers their own request" (403 `OWN_REQUEST`) in OpenAPI and AsyncAPI | text only |
| **P8** | Answer shape differs between specs (OpenAPI: discriminator on flat bodies; AsyncAPI: wrapper properties) | AsyncAPI adopts the OpenAPI shape (`oneOf` + `discriminator answerType`); one schema meaning in both | yes in AsyncAPI |
| **P9** | Menu requests miss their B1 data | Optional `occasion {dateTime, guests, dietaryNeeds, budget, tastes}`, `chef` (uuid; required when preferredProvider = CHEF), `reviewOf` (Grandma Menu help id) — required only for MENU_PROPOSAL. Specified now, built in increment 4 (planned list); the red card Q_EM_OccasionInHelpRequest is answered by this choice | additive |
| **P10** | Messaging drift across contexts: exchange `cooking-assistance` vs Grandma's `cook-assistance`; message `HelpRequestCreated` vs Grandma `HelpRequested` vs Notification `help.requested`; we consume `HelpProvided` from the routing key we publish ourselves (loop) | Keep **our** names (`cooking-assistance` exchange, `HelpRequestCreated`); consume Grandma's answers from **Grandma's** exchange and routing key (`grandma-avatar.help.provided` → queue `cooking-assistance.grandma-help-provided`); log the Grandma and Notification spec changes as open questions for their owners | yes in our consumer |
| **P11** | Error model is custom | `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail`, plus `code` (e.g. `CHEF_NOT_ALLOWED`, `OWN_REQUEST`, `OCCASION_TOO_CLOSE`, `NOT_PREMIUM`, `INVALID_STATE`); 409 for state conflicts (e.g. accepting on a Cancelled request), 422 for rule violations | yes |
| **P12** | Lists unbounded | `limit` / `cursor` paging on `GET /help-requests` and `GET /helps` | additive |
| **P13** | Lint warnings | tolerant-reader note in both `info.description`; descriptions on the 43 payload properties; examples on all messages; dotted AMQP routing keys kept as a **documented exclusion** to `house-channel-address-kebab-case` | none |
| **P14** | Length limits differ (description 5000 vs 2000; substitute name 200 vs 100) | Align to OpenAPI (5000 / 200) | AsyncAPI widening, safe for consumers |

## Decision (2026-10-31)

All proposals P1–P14 were accepted, applied to v1.0.0 as a draft revision (see `CHANGELOG.md`).

**Re-lint:** OpenAPI 0 errors / 0 warnings · AsyncAPI 0 errors / 0 warnings, with one documented exclusion.

## Reconciliation after the revision

| Slice | User stories | Board element | Contract | Status |
|---|---|---|---|---|
| B3-S01, B2-S01, B4-S01, B1-S01 | 03, 02, 04, 01a | Request help <type> | `POST /help-requests` (`createHelpRequest`) | ok |
| same | same | Help requested | publish `HelpRequestCreated` | ok (name kept: published language; board name in code) |
| B*-S02 | all | Provide Help | `POST /help-requests/{id}/helps` (`provideHelp`) + consume `GrandmaHelpProvided` | ok |
| B*-S02 | all | Help provided | publish `HelpProvided` | ok |
| B3-S03 | 03, 02, 04, 01c | Cancel help request / Help request cancelled | `cancelHelpRequest` + `HelpRequestCancelled` | ok |
| B3-S04 | 03, 02, 04, 01b | Accept help / Help accepted | `acceptHelp` + `HelpAccepted` | ok |
| B3-S05 | 03, 02, 04, 01b, 01c | Reject help / Help rejected | `rejectHelp` + `HelpRejected` | ok |
| B3-S06 | 03, 02, 04, 01c | Time out (automation) / Help request timed out | publish `HelpRequestTimedOut`; status visible via GET | ok |
| read models | all | Help request <type> / Help <type> | `getHelpRequestById`, `listHelpRequests`, `listHelpsOfHelpRequest`, `getHelpOfHelpRequest`, `listHelps` | ok |
| INV-07 / 24 | 03, 04 | notes on future requests | `previousNotes` (scope: red card US-CA-03 Q1) | ok |
| INV-28, INV-30 | 01a | premium / Chef availability | rejection codes NOT_PREMIUM, CHEF_UNAVAILABLE; source context decided in step 5 | ok (dependency open) |

No spec element is left without a slice. Every user story's outcome is observable through a GET or a published event.

**Increment per element (planned list):**

| Increment | Elements |
|---|---|
| 0 | `createHelpRequest` (PREPARATION_STEP_EXPLANATION), `getHelpRequestById`, `provideHelp`, `HelpRequestCreated`, `HelpProvided`, consume `GrandmaHelpProvided` |
| 1 | cancellation, acceptance, rejection, timeout and their events; `listHelpRequests`, `listHelpsOfHelpRequest`, `getHelpOfHelpRequest`, `listHelps`, `previousNotes` |
| 2 | INGREDIENT_SUBSTITUTE content |
| 3 | STEPS_TO_MITIGATE_CATASTROPHE content, request pictures, per-provider deadlines that differ |
| 4 | MENU_PROPOSAL content: chef, occasion, reviewOf, nextChef, NOT_PREMIUM, OCCASION_TOO_CLOSE, CHEF_UNAVAILABLE |

**Open questions for other contexts' owners:**
- The Grandma Avatar spec should publish on exchange `grandma-avatar` (today: `cook-assistance` / `help.provided`) and consume `HelpRequestCreated` (today: `HelpRequested`).
- The Notification spec expects message types `help.requested` and `help.provided`, and needs the new lifecycle events.
