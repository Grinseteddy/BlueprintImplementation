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
