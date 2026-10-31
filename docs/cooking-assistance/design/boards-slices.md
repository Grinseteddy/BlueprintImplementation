# Cook Assistance — design-level boards: legend, transcription, slice tables

Step 1.4a of the implementation blueprint. Sources: the four photos in
`Requirements/CookingAssistance/EventStormingDetail*.jpg`, cross-checked against
the graph (`Art_ESDetail_*_2026_10`). Full Detail Design Brief follows in step 2.

## 1. Legend and flavour (same on all four boards, no legend drawn)

| Sticky as drawn | Read as | Note |
|---|---|---|
| orange, sparkle icon | domain event | |
| blue, ⌘ icon | command | |
| small yellow, person icon | actor | Cook, Chef |
| yellow, briefcase icon | business object → **aggregate candidate** | no large-yellow aggregate sticky exists |
| pink, gear icon | external system | Grandma Avatar |
| green, eye icon | read model | |
| white, monitor icon | UI | Show help request, Show help |
| lilac, shield icon | **rule / constraint** (not a policy) | deviation: lilac usually = policy; no "whenever … then" wording |
| large magenta | hotspot | "Define timeouts" |
| horizontal yellow bars | context boundary | above: Recipe Catalog, Notification; below: Cooking Assistance |

**Flavour:** between process level and design level. There are commands, actors, read models, an external system and rules, but no aggregate sticky. The graph classifies the boards as process level. The briefcase business objects are taken as aggregate candidates, so every slice carries "aggregate to be confirmed in step 2". There are no policy stickies and no state stickies, except "Help REQUESTED", a shield sticky that reads like a status.

## 2. Transcription (verbatim; board order left → right, rows top → bottom)

### B1 — Help with a meal plan (`EventStormingDetailMealPlan.jpg`)
- **Row 1 (request):**
  - Cook → **Request help meal plan** → [Help request Meal plan] → **Help requested** → (arrow up into Notification) **Cook notified**
  - RM consulted: Recipe (Recipe Catalog, arrow into the business object)
  - RM: Help request Meal plan; UI: Show help request
  - Rules: Help REQUESTED · Chef needs to be selected · Only premium users allowed to ask Chef · Chef can only be asked when cook is premium user. · Chef can review a dinner plan of Grandma Avartar
- **Row 1 (answer):**
  - Cook, Chef, Grandma Avatar (external) → **Provide Help** → [Help Meal plan] → **Help provided**
  - RM: Help Meal plan; UI: Show help
  - Rules: Help Meal plan can be provided by Chef, community, or Grandma · Cook cannot answer their own request · Cook can accept the answer
- **Row 2 (cancel):**
  - **Cancel help request** → [Help request Meal plan] → **Help request cancelled**
  - RM: Help request Meal plan
  - Rules: System cancels request when answer takes too long · Cook can cancel their request when answer takes too long
  - Hotspot: **Define timeouts**

### B2 — Help with ingredient substitution (`EventStormingDetailIngredients.jpg`)
- **Row 1:**
  - Cook → **Request help ingredients** → [Help request Ingredients] → **Help requested** → **Cook notified**
  - RM consulted: Recipe Ingredient (Recipe Catalog)
  - RM: Help request Ingredient; UI: Show help request
  - Rules: Help REQUESTED · No Chef support for ingredient substitution
- **Answer:**
  - Cook, Grandma Avatar → **Provide Help** → [Help Substitutions] → **Help provided**
  - RM: Help Substitutions; UI: Show help
  - Rules: Cook cannot answer their own request · Cook can accept the answer · Help Substitutions can be provided by community, or Grandma
- **Row 2:**
  - **Cancel help request** → [Help request Ingredient] → **Help request cancelled**
  - RM: Help request Ingredient
  - Rules: System cancels … · Cook can cancel … · Hotspot Define timeouts

### B3 — Help with a preparation step (`EventStormingDetailPreparationStep.jpg`)
- **Row 1:**
  - Cook → **Request help Preparation step** → [Help request Preparation step] → **Help requested** → **Cook notified**
  - RM consulted: Recipe Preparation step
  - RM: Help request Preparation step; UI: Show help request
  - Rules: Help REQUESTED · "No Chef support for Preparation step preparation step" [doubled wording]
- **Answer:**
  - Cook, Grandma Avatar → **Provide Help** → [Help Preparation step] → **Help provided**
  - RM: Help Preparation step; UI: Show help
  - Rules: Cook cannot answer … · Cook can accept … · Help Preparation step can be provided by community, or Grandma
- **Row 2:**
  - **Cancel help request** → [Help request Preparation step] → **Help request cancelled**
  - RM: "Help request Ingredient" [copy residue]
  - Rules: System cancels … · Cook can cancel … · Hotspot Define timeouts

### B4 — Help with a catastrophe (`EventStormingDetailCatastrophe.jpg`)
- **Row 1:**
  - Cook → **Request help Catastrophe** → [Help request Catastrophe] → **Help requested** → **Cook notified**
  - RM consulted: Recipe, arrow labelled "can be provided not mandatory"
  - RM: Help request Catastrophe; UI: Show help request
  - Rules: Help REQUESTED · "No Chef support for Preparation step Catastrophe" [residue]
- **Answer:**
  - Cook, Grandma Avatar → **Provide Help** → [Help Catastrophe] → **Help provided**
  - RM: Help Catastrophe; UI: Show help
  - Rules: Cook cannot answer … · Cook can accept … · "Help Preparation step can be provided by community, or Grandma" [residue]
- **Row 2:**
  - **Cancel help request** → [Help request Preparation step] [residue] → **Help request cancelled**
  - RM: "Help request Ingredient" [residue]
  - Rules: System cancels … · Cook can cancel … · Hotspot Define timeouts

## 3. Slice tables (proposed IDs)

Aggregate candidates: **Help Request** (the "Help request <type>" business objects) and **Help** (the "Help <type>" business objects). Both appear as aggregate roots in the graph (`Con_HelpRequest`, `Con_Help`) and in SADR0004. To be confirmed in step 2.

| Slice | Trigger | Command | Aggregate (candidate) | Event(s) | RM consulted | RM updated |
|---|---|---|---|---|---|---|
| B1-S01 | Cook | Request help meal plan | Help Request (Meal plan) | Help requested | Recipe | Help request Meal plan |
| B1-S02 | Cook / Chef / community; Grandma Avatar (external) | Provide Help | Help (Meal plan) | Help provided | Help request Meal plan | Help Meal plan |
| B1-S03 | Cook; system (time — "takes too long") | Cancel help request | Help Request | Help request cancelled | Help request Meal plan | Help request Meal plan |
| B2-S01 | Cook | Request help ingredients | Help Request (Ingredients) | Help requested | Recipe Ingredient | Help request Ingredient |
| B2-S02 | Cook / community; Grandma Avatar | Provide Help | Help (Substitutions) | Help provided | Help request Ingredient | Help Substitutions |
| B3-S01 | Cook | Request help Preparation step | Help Request (Preparation step) | Help requested | Recipe Preparation step | Help request Preparation step |
| B3-S02 | Cook / community; Grandma Avatar | Provide Help | Help (Preparation step) | Help provided | Help request Preparation step | Help Preparation step |
| B4-S01 | Cook | Request help Catastrophe | Help Request (Catastrophe) | Help requested | Recipe (optional) | Help request Catastrophe |
| B4-S02 | Cook / community; Grandma Avatar | Provide Help | Help (Catastrophe) | Help provided | Help request Catastrophe | Help Catastrophe |

**Merged:** "Cancel help request" appears on all four boards with the same command, the same aggregate and the same rules. It becomes one slice, B1-S03, owned by B1. The cancel rows on B2–B4 are copies; their residues are listed above.

**Kept per board:** "Provide Help" has a different business object and different provider rules on each board (Chef only on B1), so it stays a type variant per board. All variants share the Help aggregate.

**Outside this context:**
- "Cook notified" is in the Notification lane. Cook Assistance publishes Help requested; Notification reacts. This is an integration point, not a slice of this context.
- Recipe, Recipe Ingredient and Recipe Preparation step are read models from Recipe Catalog.

**Observed, not on the board as a command:**
- "Cook can accept the answer" has no command (`Q_ESD_AcceptNoCommand`).
- "System cancels request when answer takes too long" implies a time-triggered cancel. The trigger is drawn only as a rule, and the timeout is the hotspot.

## 4. Slice plan (accepted 2026-10-31)

Decisions:
- The three missing behaviors are added as slices. Step 2 confirms them as board additions, marked *not on board*.
- Board order: **B3, B2, B4, B1**.
- B3, the first board, owns the shared lifecycle slices. The merged cancel slice B1-S03 becomes **B3-S03**.

| Slice | Board | Trigger | Command | Aggregate (candidate) | Event(s) | Source | User stories |
|---|---|---|---|---|---|---|---|
| B3-S01 | B3 | Cook | Request help Preparation step | Help Request | Help requested | board | US-CA-03 |
| B3-S02 | B3 | Cook / community; Grandma Avatar (external, via broker) | Provide Help | Help | Help provided | board | US-CA-03 |
| B3-S03 | B3 | Cook | Cancel help request | Help Request | Help request cancelled | board (all 4 boards, merged) | US-CA-03, US-CA-02, US-CA-04, US-CA-01c |
| B3-S04 | B3 | Cook | Accept help | Help Request | ⟨event name open⟩ | **added** (rule sticky "Cook can accept the answer"; Example Maps) | US-CA-03, US-CA-02, US-CA-04, US-CA-01b |
| B3-S05 | B3 | Cook | Reject help (resend with note, new deadline) | Help Request | ⟨open⟩ | **added** (Example Maps only) | US-CA-03, US-CA-02, US-CA-04, US-CA-01b, US-CA-01c |
| B3-S06 | B3 | time (automation) | Time out help request | Help Request | Help request cancelled ⟨to confirm⟩ | **added** (rule "System cancels request when answer takes too long"; hotspot Define timeouts) | US-CA-03, US-CA-02, US-CA-04, US-CA-01c |
| B2-S01 | B2 | Cook | Request help ingredients | Help Request | Help requested | board | US-CA-02 |
| B2-S02 | B2 | Cook / community; Grandma Avatar | Provide Help | Help | Help provided | board | US-CA-02 |
| B4-S01 | B4 | Cook | Request help Catastrophe | Help Request | Help requested | board | US-CA-04 |
| B4-S02 | B4 | Cook / community; Grandma Avatar | Provide Help | Help | Help provided | board | US-CA-04 |
| B1-S01 | B1 | Cook (premium) | Request help meal plan | Help Request | Help requested | board | US-CA-01a |
| B1-S02 | B1 | Chef (Grandma Avatar menu reviewed by Chef) | Provide Help | Help | Help provided | board | US-CA-01b |

Story order per board:
- B3: US-CA-03
- B2: US-CA-02
- B4: US-CA-04
- B1: US-CA-01a, US-CA-01b, US-CA-01c

Shared slices (B3-S03 to S06) are built in increment 1. Each later board adds its own rules to them test-first:
- deadlines: 24 h, 15 min / 5 min, 48 h / 24 h before the Occasion
- providers
- multiple substitutes

### User stories (split of US-CA-01, accepted 2026-10-31)

US-CA-01 is split along its Example Map rules. The parent's "so that" is kept verbatim.
- **US-CA-01a — Request a Menu from a Chef** (R1, R3, R4, R5, R9)
- **US-CA-01b — Answer and decide** (R2, R6, R7)
- **US-CA-01c — Deadlines and resets** (R8)

## 5. Walking skeleton (accepted 2026-10-31)

**US-CA-03 — Help for Meal Preparation: B3-S01 + B3-S02.**

| Layer | Reached by |
|---|---|
| HTTP in | POST help request · GET help request |
| Domain | Help Request guards: one numbered step of one recipe, text only, never a Chef |
| Database | Help Request, Help |
| Outbox → RabbitMQ out | HelpRequestCreated |
| RabbitMQ in | HelpProvided (Grandma Avatar answer) → Provide Help |
| Read model | Help request Preparation step · Help Preparation step |

Layers not reached: none.

Open: does the skeleton validate the recipe step against Recipe Catalog, or take recipeId and step number as given? Settled in steps 2 and 5.

## 6. Increments

| Increment | Scope |
|---|---|
| 0 | Walking skeleton (US-CA-03): B3-S01, B3-S02 |
| 1 | Board: Help with a preparation step — rest of US-CA-03; B3-S03 to S06 |
| 2 | Board: Help with ingredient substitution — US-CA-02 |
| 3 | Board: Help with a catastrophe — US-CA-04 |
| 4 | Board: Help with a meal plan — US-CA-01a, US-CA-01b, US-CA-01c |
