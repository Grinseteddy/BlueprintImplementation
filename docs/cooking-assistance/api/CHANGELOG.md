# Cook Assistance API — changelog

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
