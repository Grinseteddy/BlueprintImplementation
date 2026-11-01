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
