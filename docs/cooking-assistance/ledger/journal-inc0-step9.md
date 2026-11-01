# Journal — increment 0, step 9 (Inbound adapters)

## Slice check
- Flags: unbuilt operations (useTags → all 10 operations per interface), helpProviderType not in the command, inline link schemas, 400 vs 422 for HelpRequestInvariants, missing Problem codes, OpenAPI 3.1 validator.
- Decisions: explicit 501; step 8 r1; step 4 r1 (all three); error codes accepted.
- Consequence shown before approval: CHEF_NOT_ALLOWED, TOO_MANY_PICTURES and no-provider are schema rules too → 400 on REST.

## Step 4 r1 / r2
- r1: OpenAPI 1.0.1, Spectral no errors.
- r2: found while probing the AsyncAPI CLI 6.2.1 — `asyncapi validate` failed with 17 errors (bindings `amqp: $ref` one level too deep). Fixed in 12 places; CLI valid, Spectral no errors. Step 4's Spectral lint had not caught it.

## Step 8 r1 (ProvideHelp providerType)
- Scaffold: Command gains providerType; call sites pass COMMUNITY. Predicted green, 2 skipped — matched.
- Item 1 (GRANDMA_AVATAR → NotAuthorized): predicted Red "Expecting code to raise a throwable" — matched.
- Green: **process slip** — run without a stated prediction (would have been: all green, item 2 skipped; that is what happened).
- Item 2 reworded before activation: actor needs cook, community and chef, otherwise require(COMMUNITY) alone refuses and the test does not discriminate.
- Item 2 green on activation as predicted; deterministic trial (condition `== GRANDMA_AVATAR`) → only item 2 failed, as predicted; restored green.
- Refactor agent: admitCommunityAnswer kept; tests unchanged (diff -r); verify ×2 green.

## Step 4 r3 and 9a (generation)
- Probes: Modelina 5.10.1 supports AsyncAPI ≤ 3.0.0 (generated nothing from 3.1.0); CLI has no records option; 10 inline const/enum schemas → AnonymousSchema_N; OpenAPI request bodies lacked `required: true`.
- r3 approved and applied (AsyncAPI 3.0.0, titles, required bodies; OpenAPI/AsyncAPI 1.0.2); CLI valid, Spectral no errors (1 info: latest version 3.1.0).
- Generators: openapi-generator 7.26.0; Modelina 5.10.1 via tools/codegen script, Node v22.22.1 via frontend-maven-plugin 1.15.1. Reproducibility: first diff only @Generated timestamps → hideGenerationTimestamp → 0 diff lines. verify green.

## 9b test lists
- 53 inactive items (A 17, B 17, C 1, D 4, E 10, F 1, G 3); verify green with 50 + 3 skipped, as predicted.

## Cycles (HelpRequestsControllerTest)
- A1: Red 404 as predicted; Green controller + mapper + CurrentActor (static, WebMvcTest ignores @Component). Refactor: linkTo inlined — rerun without stated prediction (minor slip).
- A2: Red "Content type not set" as predicted. Green: **mismatch** — "nothing saved" failed: the fake is a singleton in the cached context (A1's request). Fix approved: InMemoryHelpRequestRepository.clear() + @BeforeEach.
- A3: Red 'No value at JSON path "$.code"' as predicted; Green handleHttpMessageNotReadable + validationFailed().
- A4: Red 201 as predicted (mapper wraps null → HowToStepId(null) bypasses INV-02). Green: OpenApiRequestValidation (networknt 1.5.9, OpenAPI 3.1 dialect, formats on), spec on classpath via POM resources. Risk recorded: mapper relies on edge validation (no unreachable null branch).
- A5: reworded (no "formats checked" — Jackson rejects too); green on activation; trial formats off → still green as predicted. Format proof moved to E4.
- A6: green on activation; trial validator never throws → A6 error ChefNotAllowed as predicted, **mismatch: A4 failed too (201)** — prediction incomplete; A4 depends only on the validator.
- A7: Red 201 (generated defaultValue hides the header) as predicted; Green OpenApiParameterValidation interceptor. Refactor: OpenApiContract shared by both validators and SpecSchemas.
- A8: green on activation; trial (no handleHttpMessageNotReadable) → only A8 fails on $.code, as predicted.
- A9: Red 201 as predicted (risk from A4); Green switch on type + NotImplemented → 501. Refactor: problem(status, code, title).
- A10: Red 403 (CSRF before auth) as predicted; Green SecurityConfiguration (stateless, CSRF off, entry point → 401 Problem). Refactor: Problems.of shared. verify → 29 IT errors (no JwtDecoder) as predicted → user chose JWKSource design: decoder from JWKSource bean (prod: remote JWKS from cooking-assistance.security.jwk-set-uri, set by larder-app in step 12; tests: @Primary TestSigningKeys in fixtures; ITs: never-fetched placeholder URI). verify green.
- A11: Red 201 as predicted; Green audience validator only (minimal). A11b added (approved): Red 201; Green createDefaultWithIssuer.
- A12: green on activation; trial (issuer validator only) → only A12 fails, as predicted.
- A13: Red ServletException(IllegalArgumentException "Invalid UUID string: lena") as predicted; Green sub UUID validator. Refactor: null check instead of NPE catch.
- A14: Red 201 as predicted; Green POST /help-requests/** → SCOPE_help:write, 403 INSUFFICIENT_SCOPE handler. Refactor: writeProblem.
- A15: Red NotAuthorized error as predicted; Green 403 NOT_AUTHORIZED. A15b added: Red NPE (roles null) as predicted; Green missing claim = no roles. A15c added: Red "No enum constant Role.ADMIN" as predicted; Green unknown roles grant nothing.
- A16: Red RuntimeException error as predicted; Green catch-all 500 INTERNAL_ERROR (logged, no detail).
- A17: Red 500 (placeholder + catch-all) as predicted; Green NotImplemented in the 3 unbuilt methods.
- Step 4 r4 (approved): global security help:read only → OpenAPI 1.0.3; Spectral no errors. GET → SCOPE_help:read rule on the planned list for step 11.
- Unit A: 20/20 (17 planned + A11b, A15b, A15c). Paused by the user after unit A; nothing committed since b6d94a2.

## Resume 2026-11-01
- verify green (33 + 3 skipped) as predicted; 4 commits 7d5ea33, 0990170, 7a267fe, db61d23.

## Cycles (HelpsControllerTest)
- B1: Red 404 as predicted; Green HelpsController (5 unbuilt → NotImplemented) + HelpMapper; compile gate caught a wrong listHelps signature (fixed). Refactor: Links.absolute shared by both mappers.
- B1b added (approved): Red 500 (images null → NPE) as predicted — first removal string did not match the text block indentation, fixed with replaceAll before the run; Green absent images → empty list.
- B2–B5: each green on activation (run per activation); trial validator off → all stay green as predicted (double guard: bean validation / Jackson).
- B6: Red 500 as predicted; Green HelpRequestNotFound → 404.
- B7: Red 500 as predicted; Green DomainRejection handler (403, code, rule). B8 green on activation; trial status 422 → B7 and B8 fail, as predicted. B9: Red 403 → Green INVALID_STATE 409. B10: Red 403 → Green status table (403 actor rules, 409 state, 422 rest; spec P11).
- B11: Red 500 as predicted; Green ConcurrencyConflict → 409 CONCURRENT_UPDATE.
- B12: green on activation; trial mapper always COMMUNITY → only B12 fails (201), as predicted. B13: green; trial NotAuthorized → 401 → A15, A15b, B12, B13 fail, as predicted.
- B14: Red 500 (ClassCastException) as predicted; Green pattern switch over the answer kind in HelpMapper.
- B15: green; trial exact path matcher → only B15 fails (201), as predicted. B16: green; trial no catch-all → A16 and B16 error, as predicted. B17 (7 parameterized): green; trial no 501 handler → 7 B17 cases + A9, A17, B14 fail, as predicted.
- Unit B: 18/18 (17 planned + B1b), 0 behavioral mismatches.
- Unit C (renamed ProblemDetailsAdviceTest to match the class): C1 (9 cases) green on activation; trial default 403 → 6 × 422 cases + B10 fail, as predicted. 1/1.
- Unit D: D1 Red no $.code → handleNoResourceFoundException; D2 Red no $.code (status and Allow already right) → 405 override keeping Allow; D3 Red no $.code → 415 override keeping Accept; D4 Red 500 as predicted (revised at activation from "green": networknt parsed the body first, "Invalid input", confirmed in the log) → body advice parses with Jackson, unparsable → RequestValidationFailed. Refactor: problem(..., headers). 4/4.
- Unit E (GrandmaHelpProvidedConsumerTest): scaffold Outcome + handle returning null. E1 Red "expected ACK but was null" → Jackson into the generated record + GrandmaAnswerMapper (anticorruption) → ACK. E2 Red ACK (title missing stored) → AsyncApiContract (draft-07, formats) + InvalidMessage → DEAD_LETTER. E3 Red NPE → MessageHeader validation. E4 green; trial formats off → error "Invalid UUID string: abc" as predicted (format proof promised at A5). E5 Red UncheckedIOException(JsonParseException) → unparsable → InvalidMessage. E6 green; trial random messageId → E6 fails (2 helps) as predicted, **mismatch: E1 failed too** (E1 also asserts M1 recorded — prediction incomplete). E7 Red HelpRequestNotFound → DEAD_LETTER; E8 Red ProviderNotAsked → DomainRejection DEAD_LETTER; E9 Red RuntimeException → RETRY (logged without payload); E10 Red RETRY (ClassCastException caught) → type switch + AnswerKindNotBuilt → DEAD_LETTER. 10/10.
- Unit F: scaffold empty MessageConsumers registry; Red "could not find consumeGrandmaHelpProvided" → registered; gate trial (made-up receive operation in the spec) fails as predicted; spec restored from git. 1/1.
- Unit G (InboundWiringIT; PersistenceTestApplication and PostgresTestContainer made public): G1 green, trial no @RestController → 404 as predicted; G2 green, trial on HelpsController → only G2 404; G3 Red NoSuchBeanDefinitionException → @Component on the consumer. 3/3.
- Finish: verify ×2 green; a "1 skipped" in my sum was a stale report of the renamed ProblemMapperTest (no class) — clean verify: 105 fast + 32 slow, 0 skipped. Generation reproducible (0 diff). REST gate trial: made-up operation → compile error in HelpRequestsController; spec restored. Leak grep: none. 8 ifs in adapter/in — all contract or token checks.
- Slice refactoring B3-S01 (isolated agent): kept notFound(), OpenApiContract.operationServedBy(handler), AcceptedTokens (ADR0012 token policy out of SecurityConfiguration); tests unchanged; clean verify green. Proposals: rejection category in the domain → inc 1 (with NOT_REQUESTER); NoHelpProviderChosen code VALIDATION_FAILED would answer 422 via the domain vs 400 in the spec (unreachable over REST) → inc 1; violation messages unused → A2b added (approved): Red no $.detail → Green detail = schema violations.
- Slice refactoring B3-S02 (isolated agent): kept Outcome javadoc; undid ADR0015 classification in Outcome; tests unchanged; clean verify ×2 green (106 fast + 32 slow).
- Step 9 approved 2026-11-01; commits 31e7177, 66a0199, b8cc093, 5505893 (intermediate states rebuilt from the refactor snapshots; the refactor state passed the fast suite).
