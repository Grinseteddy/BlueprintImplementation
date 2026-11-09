# Cook Assistance API — changelog

## AsyncAPI 1.0.9 — 2026-11-09 (step 4 r20, from step 9 of increment 2)

| Change | Why |
|---|---|
| `consumeGrandmaHelpProvided`: an answer repeating a substitute the cook rejected earlier on this request is acknowledged and ignored, never dead-lettered | decided by the user: a repeat is a normal event, not a broken message. Description only; no schema change |

## OpenAPI 1.0.13 — 2026-11-08 (step 4 r19, from the test list of increment 2, S2)

| Change | Why |
|---|---|
| A request of type `INGREDIENT_SUBSTITUTE` requires `substituteRequirements` | INV-11: one ingredient "with its substitute requirements". Stricter, but the operation answers 501 for this type so far, so no caller breaks |
| `version` header accepts 1.0.13 | |

## AsyncAPI 1.0.8 — 2026-11-08 (step 4 r18, from the readiness gate of increment 2, Q-D1)

| Change | Why |
|---|---|
| `HelpRequestTimedOut.occurredAt` is the moment the time-out was detected | decided by the user (D-2, Q-D1): the event records when the system found the time-out. The description said "when the last deadline passed"; the service has always sent the detection time. Clarification, no shape change |

## OpenAPI 1.0.12 — 2026-11-08 (step 4 r17, from the security review of increment 1, SEC-14)

| Change | Why |
|---|---|
| `HelpRequest.previousNotes` is limited to the 10 most recent notes, oldest first (`maxItems: 10`) | a list page repeated the whole note history of each step, growing without limit (OWASP API4). Narrows what a response can hold; the items are unchanged |
| `version` header accepts 1.0.12 | |

## OpenAPI 1.0.11 — 2026-11-08 (step 4 r16, from the reverse check of the rework, step 13 of increment 1)

| Change | Why |
|---|---|
| `cancelHelpRequest` and `Cancellation.note` state that a blank note counts as no note | INV-08 (step 3 r8, inspection D-3). Clarification, no shape change |
| `acceptHelp` states that a help already rejected cannot be accepted (409 INVALID_STATE) | INV-39 was built and tested but missing from the description. Clarification |
| `version` header accepts 1.0.11 | |

## AsyncAPI 1.0.7 — 2026-11-08 (step 4 r15, from the reverse check of the rework, step 13 of increment 1)

| Change | Why |
|---|---|
| `consumeGrandmaHelpProvided` states that a late answer is acknowledged and ignored, never dead-lettered | Q-A2, ADR0015 addition of 2026-11-08: "the same rules as REST" did not say what happens to a refused message. Clarification, no shape change |

## AsyncAPI 1.0.6 — 2026-11-07 (step 4 r14, from the Fagan inspection of increment 1, A-1)

| Change | Why |
|---|---|
| `MessageHeader` gains the optional `traceparent` (W3C Trace Context, `00-<trace id>-<span id>-<flags>`) | ADR0020: the trace id travels with AMQP messages; the inspection (A-1) found only `correlationId` on published messages. Optional, so additive: absent when the command ran without a trace |

## OpenAPI 1.0.10 — 2026-11-06 (step 4 r13, from the reverse check of increment 1, step 13)

| Change | Why |
|---|---|
| `HelpRequest.previousNotes` describes the same step of the same recipe; notes about the same dish are marked planned | the description said "recipe step or dish"; INV-07 (red card Q1) and the ADR0016 addition decide the same step of the same recipe. Clarification, no shape change |
| `Problem.code` marks `FIELD_NOT_ALLOWED` as planned | nothing in increment 1 produces it; it belongs to kinds of help with fields of their own. Clarification |
| `version` header accepts 1.0.10 | |

## OpenAPI 1.0.9 — 2026-11-05 (step 4 r12, from step 12 of increment 1)

| Change | Why |
|---|---|
| `CancellationRequest` gains the example `withNote`, `RejectionRequest` the example `warmWater`; `version` header accepts 1.0.9 | the example-based contract check and Schemathesis need request examples for cancelHelpRequest and rejectHelp. Additive: no shape changes |
| `Problem.instance` is a `uri-reference` (was `uri`) | Schemathesis: the service answers a relative `instance` as RFC 9457 allows; the spec required an absolute URI. Correction of the documented format, no change in what the service sends |
| `createHelpRequest`, `provideHelp` and `rejectHelp` declare 501 `NotImplementedResponse` | Schemathesis: the variants later increments build (other request types and answer kinds, `nextChef`) answer 501, which was undeclared. Additive |
| Every operation declares 503 `ServiceNotAvailableResponse`; its 500 now points to `UnexpectedErrorResponse` (it pointed to `ServiceNotAvailableResponse`); `SERVICE_UNAVAILABLE` named in `Problem.code` | QS-05 (step 12, DatabaseOutageIT): without the database the service answers 503; the spec had no 503 and labelled 500 "Service unavailable". Additive for 503; 500 keeps the Problem schema |

## OpenAPI 1.0.8 — 2026-11-04 (step 4 r11, from step 11 of increment 1, later question L6)

| Change | Why |
|---|---|
| `listHelpRequests`, `listHelpsOfHelpRequest` and `listHelps` state their order: oldest first, in creation order, no item repeated on a later page; `cursor` states that a cursor the API did not issue answers 400; `version` header accepts 1.0.8 | keyset paging needs a stated order (L6, decided by the user 2026-11-04). Clarification: no request or response shape changes |

## AsyncAPI 1.0.5 — 2026-11-02 (step 4 r10, from the skeleton review of increment 0)

| Change | Why |
|---|---|
| `GrandmaMessageHeader` declares `type: object` at its top level | Spectral `asyncapi-3-headers-schema-type-object` (1 error) once Spectral ran in `./mvnw verify` (ADR0018). No change in the headers accepted: both `allOf` parts already required an object |

## OpenAPI 1.0.7 — 2026-11-01 (step 4 r9, from step 14 of increment 0)

| Change | Why |
|---|---|
| `PAYLOAD_TOO_LARGE` named in the `Problem.code` description (413 for a request body over 1 MB, ADR0019); `version` header accepts 1.0.7 | security review SEC-01: a 3 MB body was accepted. Additive |

## AsyncAPI 1.0.4 — 2026-11-01 (step 4 r8, from step 14 of increment 0)

| Change | Why |
|---|---|
| `images` of PreparationStepExplanation and CatastropheMitigation must match `^https://` | security review SEC-07: non-https picture links from the Grandma Avatar were stored and republished. Narrowing, aligned with OpenAPI 1.0.6 |

## OpenAPI 1.0.6 — 2026-11-01 (step 4 r8, from step 14 of increment 0)

| Change | Why |
|---|---|
| `Pictures` items must match `^https://`; `INVALID_PICTURE_LINK` named in the `Problem.code` description (422, INV-38); `version` header accepts 1.0.6 | security review SEC-07: `javascript:`, `file:` and metadata URLs were stored and republished as picture links. Narrowing: such links were never meant to be valid |

Note (2026-11-02, reverse check of step 14): over REST the `Pictures` pattern answers first, so a client sees 400 `VALIDATION_FAILED`; `INVALID_PICTURE_LINK` (422) is the domain's code and shows where the schema does not run first (Grandma's messages end as dead letters).

## AsyncAPI 1.0.3 — 2026-11-01 (step 4 r7, from step 14 of increment 0)

| Change | Why |
|---|---|
| `GrandmaHelpProvided` headers use the new `GrandmaMessageHeader`: `MessageHeader` with `source` fixed to `grandma-avatar` | security review SEC-08: a message from another source was processed. Narrowing for producers other than the Grandma Avatar, who never had the right to send it |

## OpenAPI 1.0.5 — 2026-11-01 (step 4 r6, from step 13 of increment 0)

| Change | Why |
|---|---|
| `NOT_ACCEPTABLE` named in the `Problem.code` description (406 when the client accepts no media type the operation produces); `version` header accepts 1.0.5 | Fagan D-02: every Problem carries a code, also for errors the framework raises. Additive, non-breaking |

## OpenAPI 1.0.4 — 2026-11-01 (step 4 r5, from step 12 of increment 0)

| Change | Why |
|---|---|
| Request-body examples for createHelpRequest (`preparationStep`) and provideHelp (`preparationStepExplanation`); examples on the `helpRequestId` and `helpId` path parameters; `version` header accepts 1.0.4 | the example-based contract check (and later Schemathesis) had no test data |

## OpenAPI 1.0.3 — 2026-10-31 (step 4 r4, from step 9 of increment 0)

| Change | Why |
|---|---|
| Global security requires `help:read` only (was `help:read` and `help:write` in one requirement); write operations keep their `help:write` override; `version` header accepts 1.0.3 | one requirement listing both scopes means both are needed for every read; the intent is read → help:read, write → help:write. Relaxing, non-breaking |

## 1.0.2 — 2026-10-31 (step 4 r3, from step 9 of increment 0)

Wire format unchanged.

| Spec | Change | Why |
|---|---|---|
| OpenAPI | `required: true` on the create-help-request, create-help and rejection request bodies; `version` header accepts 1.0.2 | an empty body was spec-valid; the generator emitted `@RequestBody(required = false)` |
| AsyncAPI | `asyncapi: 3.0.0` (was 3.1.0); the document uses no 3.1 feature and validates as 3.0.0 | Modelina 5.10.1 supports AsyncAPI up to 3.0.0 and generated nothing from 3.1.0 |
| AsyncAPI | `title` on the 10 inline const/enum schemas (`*AnswerType`, `*Status`) | Modelina named them `AnonymousSchema_N` |

## 1.0.1 — 2026-10-31 (step 4 r1, from step 9 of increment 0)

Wire format unchanged; no breaking change.

### OpenAPI

| Change | Why |
|---|---|
| Inline link responses named `HelpRequestLink` and `HelpLink` (component schemas) | generators invented `CreateHelpRequest201Response` / `ProvideHelp201Response` |
| Schema violations, including the type-dependent `HelpRequestInvariants`, a chef outside a menu request and more than 10 pictures or images, answer 400 VALIDATION_FAILED; the 422 lists keep only the rules that depend on state | the descriptions promised 422 for rules the schema already rejects with 400 |
| Problem codes added: CONCURRENT_UPDATE (409), NOT_AUTHORIZED, INSUFFICIENT_SCOPE (403), UNAUTHENTICATED (401), NOT_FOUND (404), METHOD_NOT_ALLOWED (405), UNSUPPORTED_MEDIA_TYPE (415), INTERNAL_ERROR (500), NOT_IMPLEMENTED (501) | errors the adapter produces are named |
| `version` header accepts `1.0.0` and `1.0.1`, default `1.0.1` | additive |

### AsyncAPI (1.0.1, step 4 r2)

| Change | Why |
|---|---|
| The 12 channel and operation `bindings` reference the binding components directly (`bindings: $ref`), not under an extra `amqp:` key | the AsyncAPI parser (CLI 6.2.1) rejected the document with 17 errors; Spectral had not caught it |

## 1.0.0 — revision 2026-10-31 (step 4, before any consumer)

v1.0.0 had no consumers, so it was revised in place. Sources: `SamplesDddMeetAi/Chapter12/{OpenAPIs,AsyncAPIs}/ClaudeFable51/`. Proposals: `spec-review.md`.

### OpenAPI

| Change | Proposal | Breaking vs the original draft |
|---|---|---|
| Added `POST /help-requests/{id}/cancellation` (optional note) | P1 | — |
| Added `POST …/helps/{helpId}/acceptance` | P1 | — |
| Added `POST …/helps/{helpId}/rejection` (note, optional nextChef) | P1 | — |
| Removed `DELETE /help-requests/{id}` | P1 | yes |
| Removed `PATCH /help-requests/{id}`, `PATCH /helps/{id}`, `DELETE /helps/{id}` | P3 | yes |
| Helps nested under their request: `POST`/`GET /help-requests/{id}/helps[/{helpId}]`; `POST /helps` removed; `GET /helps` kept as a provider query | P4 | yes |
| Status `OPEN, ANSWERED, CLOSED, CANCELLED` (open enum) | P5 | — |
| Added `askedProviders` (deadline and state per provider), `suggestedProviders`, `notes`, `previousNotes`, `acceptedHelpLink`, `helpsLink`; `Help.isAccepted`, `rejectionNote`, `helpRequestLink` | P5 | — |
| `ingredients[]` → `ingredient` + `substituteRequirements` | P6 | yes |
| Request `pictures` (≤ 10, catastrophe only) | P6 | — |
| Answer images ≤ 10, also on catastrophe answers | P6 | — |
| A substitute can be a mix: `substituteIngredients[1..10]` | INV-12 | yes |
| "A cook never answers their own request" (403 OWN_REQUEST) | P7 | — |
| Menu fields `chef`, `occasion`, `reviewOf` with type invariants; CHEF only for MENU_PROPOSAL and then alone | P9 | — |
| Errors as `application/problem+json` with domain `code`; 409 INVALID_STATE, 422 rule violations | P11 | yes |
| `limit` / `cursor` paging; list responses are pages `{items, nextCursor}` | P12 | yes (list shape) |
| Tolerant-reader note; link response carries `helpRequestId` plus an OpenAPI `link` | P13 | — |

### AsyncAPI

| Change | Proposal | Breaking vs the original draft |
|---|---|---|
| Added `HelpAccepted`, `HelpRejected`, `HelpRequestCancelled`, `HelpRequestTimedOut` on `cooking-assistance.help.{accepted,rejected}` and `cooking-assistance.help-request.{cancelled,timed-out}` | P2 | — |
| Consumes the Grandma Avatar's answers from queue `cooking-assistance.grandma-help-provided`, bound to exchange `grandma-avatar`, key `grandma-avatar.help.provided`. Replaces the loop on its own `HelpProvided` | P10 | yes |
| `Answer` uses the OpenAPI shape (`oneOf` + discriminator `answerType`) | P8 | yes |
| Payloads aligned with the OpenAPI spec: `askedProviders`, `ingredient`, `pictures`, menu fields, `occurredAt`, open enums | P5, P6, P9 | yes |
| Descriptions on every property, examples on every message, tolerant-reader note | P13 | — |
| Lengths aligned (description 5000, name 200) | P14 | — |
| AMQP dotted routing keys: documented lint exclusion in `rulesets/asyncapi.spectral.yaml` | P13 | — |
