# Journal — increment 3, step 10 (Outbound messaging)

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01, B4-S02 and B3-S06 (r21).
EXACT Coding at 88fb3f9, autonomy full-hitl. Input reused from steps 4 (AsyncAPI 1.0.12), 5 (ADR0015, ADR0021), 7 and 8 (user, 2026-11-16). Modelina unchanged.

## Slice check
- No new event type in increment 3: the six HelpRequestEvent types keep their decisions (all published, one send operation each). A side-only time-out raises no event (r21).
- Payloads are built in step 7 under the message field names: HelpRequestCreated gains `pictures` (absent when none), HelpProvided carries a CatastropheMitigation (recipe optional, images optional). The relay's OutboxMessageMapper picks the channel by event type and checks headers and payload against the spec.
- The relay does not depend on the kind of help, so retries, order, dead letters and concurrency stay with OutboxRelayIT (as in increment 2).
- Nothing flagged: identity header messageId, key = aggregate id, delivery columns and claim index exist since increment 0.

## Test list
- 8 items: OutboxMessageMapperTest 5, OutboundWiringIT 3. Inactive list compiles; fast suite green, the 5 mapper items skipped.
- Found before approval: AsyncAPI HelpRequestCreatedPayload.pictures had no `^https://` pattern (OpenAPI Pictures and AsyncAPI CatastropheMitigation.images have it; INV-38, SEC-07). User: fix in step 4 → r24, AsyncAPI 1.0.13 + CHANGELOG; the SEC-20 exceptions leave oasdiff-err-ignore.txt (removed with the next spec change). Spectral clean (one known info), fast suite green, QS-15 gate vs origin/main exit 0, as predicted (oasdiff 1.34.1 darwin_all, sha256 checked against the release's checksums.txt, which also confirms CI's linux pin).
- Test list approved by the user 2026-11-16 (item 3 kept on the corrected spec).

## Cycles
- M1 green on activation, as predicted (fixture helpRequestCreatedForACatastrophe in OutboxPayloads). Trial: classpath AsyncAPI pictures maxItems 1 → "InvalidOutgoingMessage /pictures: must have at most 1 items but found 2", as predicted; restored (cmp with source). Fast suite green.
- M2 green on activation, as predicted (recipe and pictures removed from the fixture; the test asserts their absence). Trial: classpath copy requires pictures → "InvalidOutgoingMessage: required property 'pictures' not found", as predicted; restored (cmp). Fast suite green.
- M3 green on activation, as predicted (r24 pattern). Trial: pattern removed from the classpath copy → "Expecting code to raise a throwable", as predicted (the gap r24 closed); restored (cmp). Fast suite green.
- M4 green on activation, as predicted (fixture helpProvidedWithAMitigation). Trial: classpath CatastropheMitigation.images maxItems 1 → InvalidOutgoingMessage containing "/answer/images: must have at most 1 items but found 2", as predicted (amid the oneOf branch messages); restored (cmp). Fast suite green.
- M5 green on activation, as predicted. Trial: explanation removed from CatastropheMitigation.required in the classpath copy → "Expecting code to raise a throwable", as predicted; restored (cmp). OutboxMessageMapperTest fully active; fast suite green, 0 skipped.
- W1 green on activation, as predicted (helper lenaAsksAboutBurntChicken over HTTP; failsafe 7 tests, 2 skipped, 0 failures). Trial: persistence OutboxEventMapper.pictures reversed → containsExactly failed with the links swapped, as predicted; restored (git diff empty).
- W2 green on activation, as predicted (Grandma's mitigation through the real ProvideGrandmaAvatarHelp bean; failsafe 7 tests, 1 skipped). Trial slip: first run void — `List` is not imported in AnswerMessageMapper, compilation error, no test ran; restored. Rerun with java.util.List.<String>of() → "Expecting actual: [] to contain exactly …" on the images, as predicted; restored (git diff empty).
- W3 green on activation, as predicted (two TimeOutDueHelpRequests passes with the real repository and unit of work, clocks +6 and +16 min; failsafe 7/7, 0 skipped). Trial: domain timeOut with `if (true)` in place of everySideTimedOut → first relay assertion failed (created + timed-out vs created), as predicted; restored (git diff empty). All 8 items active.
- No production code changed in this step (spec r24, fixtures and tests only), so no refactor agent runs (as in increment 2).

## Finish
- Publish-decision gate trial: made-up HelpRequestArchived in the sealed union → "the switch expression does not cover all possible input values" in OutboxEventMapper, as predicted; restored (git diff empty).
- Leak grep in domain and application: none. `if`s in adapter/out/messaging unchanged (broker confirm and return, traceparent header, spec check, claim-round loop, held-back aggregates). No production diff in this step.
- clean verify run 1: cooking-assistance green (583 fast; 204 + 14 + 69 slow), larder-app failed on infrastructure — the RabbitMQ Testcontainer timed out waiting for "Server startup complete", the acceptance context failed to load, 48 of 67 errors carry that one cause; not a behavioral result. Run 2 fully green. Run 3 started so that two consecutive runs are green.
- clean verify run 3 fully green (583 fast; 204 + 14 + 69 slow, 0 skipped) — runs 2 and 3 are two consecutive green runs.
- Handed on: nothing new (relay behavior unchanged; the real broker client stays proven by AmqpAdaptersIT in step 12).
- Step 10 approved by the user 2026-11-16 (approve and commit).
