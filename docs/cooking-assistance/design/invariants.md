# Invariants and acceptance scenarios — Cook Assistance

Step 3 of the implementation blueprint. Grouped board → user story → rule → example.
Aggregate for every rule: **Help Request** (Help is an entity in it, decision D1), unless marked policy or read model.
Examples are verbatim from the Example Map photos (`Requirements/CookingAssistance/*_example_map.jpg`), checked against the graph.
Each example is written as **Given/When** (the situation) **Then** (the outcome). Step 12 separates Given from When in the feature files.
Kinds: *quantitative* and *cardinality* rules get a property test in step 6.

## B3 — Help with a preparation step

### US-CA-03

#### INV-01 — US-CA-03 R1: Help with a preparation step goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask
- **Slice / command:** B3-S01 · Request help Preparation step · **kind:** cardinality
- **Guard / outcome:** ChefNotAllowed — a Chef is never offered or accepted as provider; providers ⊆ {Community, Grandma Avatar}, 1..2
- **Sources:** map; board 'No Chef support for Preparation step' + 'Help Preparation step can be provided by community, or Grandma'; model 'Chef only for Menu proposal'; glossary preferred Provider 1..2
  - `US-CA-03 R1.E1` **Given/When** Lena asks Grandma about step 4 of Hollandaise **Then** Grandma receives it
  - `US-CA-03 R1.E2` **Given/When** Lena asks the Community and Grandma together **Then** both get the same request
  - `US-CA-03 R1.E3` **Given/When** Tom (free plan) asks about step 2 of his bread **Then** request sent
  - `US-CA-03 R1.E4` **Given/When** Marco proposed the Hollandaise; Lena looks for help **Then** Marco not offered

#### INV-02 — US-CA-03 R2: A request can only have one step: one numbered step of one recipe
- **Slice / command:** B3-S01 · Request help Preparation step · **kind:** cardinality
- **Guard / outcome:** exactly one numbered step of one recipe; steps cannot be added to an open request (new request instead)
- **Sources:** map; model 'howTo Step only for Preparation Step Explanation', 'recipe mandatory'; glossary howTo 0..1
  - `US-CA-03 R2.E1` **Given/When** Step 4: "How do I keep the sauce from splitting?" **Then** shows recipe, step 4, question
  - `US-CA-03 R2.E2` **Given/When** Unsure about steps 4 and 6 **Then** two requests
  - `US-CA-03 R2.E3` **Given/When** Lena tries to add step 6 to her open request on step 4 **Then** not added, new request offered

#### INV-03 — US-CA-03 R3: A request is text only
- **Slice / command:** B3-S01 · Request help Preparation step · **kind:** other
- **Guard / outcome:** PicturesNotAllowed — the request carries text only
- **Sources:** map; contradicts glossary picture 0..* (resolved per type: maps)
  - `US-CA-03 R3.E1` **Given/When** Lena tries to attach a photo of the split sauce **Then** not added, sent as text

#### INV-04 — US-CA-03 R4: An answer may include pictures; accepting an answer closes the whole request
- **Slice / command:** B3-S02 · Provide Help · **kind:** other
- **Guard / outcome:** an answer may carry pictures; several providers may answer (Answered can still be answered)
- **Sources:** map R4 (E1, E3); model v2 'Answered help requests can be still answered'
  - `US-CA-03 R4.E1` **Given/When** Grandma: "butter in a thin stream" + 3 photos **Then** Lena sees answer and photos
  - `US-CA-03 R4.E3` **Given/When** Jonas and Mia answer; Lena accepts Mia's **Then** closed

#### INV-05 — US-CA-03 R4: An answer may include pictures; accepting an answer closes the whole request
- **Slice / command:** B3-S04 · Accept help · **kind:** other
- **Guard / outcome:** accepting one answer closes the whole request for every provider → Closed
- **Sources:** map R4 (E2–E4); board 'Cook can accept the answer'; model v2 'At least one Help must be accepted'
  - `US-CA-03 R4.E2` **Given/When** Lena accepts Grandma's answer **Then** closed
  - `US-CA-03 R4.E3` **Given/When** Jonas and Mia answer; Lena accepts Mia's **Then** closed
  - `US-CA-03 R4.E4` **Given/When** Both asked, Lena accepts Grandma's **Then** closed for Community too

#### INV-06 — US-CA-03 R5: Not accepted: sent again to the same providers with a note on what didn't work, new 12 h deadline
- **Slice / command:** B3-S05 · Reject help · **kind:** quantitative
- **Guard / outcome:** resent to the same providers with a note; new deadline = rejection time + 12 h
- **Sources:** map; board 'Define timeouts' answered
  - `US-CA-03 R5.E1` **Given/When** Fri 20:00, Lena: "warm water didn't help" **Then** sent again to Grandma with note, deadline Sat 08:00
  - `US-CA-03 R5.E2` **Given/When** Both asked, nothing accepted **Then** sent again to both

#### INV-07 — US-CA-03 R6: Notes are kept and shown on future requests for the same step
- **Slice / command:** B3-S01 (policy/read model) · Request help Preparation step · **kind:** other
- **Guard / outcome:** notes of earlier requests on the same step are shown — across Help Request instances → read model / query, not an aggregate guard
- **Sources:** map; red card Q1 deferred → increment 1
  - `US-CA-03 R6.E1` **Given/When** Next month Lena asks about step 4 again **Then** note "warm water didn't help" shown

#### INV-08 — US-CA-03 R7: The Cook can cancel an open request at any time, with an optional note
- **Slice / command:** B3-S03 · Cancel help request · **kind:** other
- **Guard / outcome:** the requester can cancel an Open or Answered request at any time, optional note; providers see the note → Cancelled
- **Sources:** map (US-CA-02 R6, US-CA-03 R7, US-CA-04 R8 — one card); board 'Cook can cancel their request'; decision D4
  - `US-CA-03 R7.E1` **Given/When** Lena cancels: "an ice cube saved it" **Then** closed, providers see the note
  - `US-CA-03 R7.E2` **Given/When** Lena cancels without a note **Then** closed

#### INV-09 — US-CA-03 R8: Unanswered after 12 h: cancelled; a provider not yet asked is suggested
- **Slice / command:** B3-S06 · Time out help request · **kind:** quantitative
- **Guard / outcome:** no answer within 12 h → Help request timed out (Cancelled); a provider not yet asked is suggested; an answer at 11:59 h counts
- **Sources:** map; board 'System cancels request when answer takes too long'; hotspot 'Define timeouts'
  - `US-CA-03 R8.E1` **Given/When** Only Community asked Fri 18:00, silent until Sat 06:00 **Then** cancelled, Grandma suggested
  - `US-CA-03 R8.E2` **Given/When** Only Grandma asked Fri 18:00, silent until Sat 06:00 **Then** cancelled, Community suggested
  - `US-CA-03 R8.E3` **Given/When** Both asked, both silent 12 h **Then** cancelled, Lena told
  - `US-CA-03 R8.E4` **Given/When** Jonas answers Sat 05:59 **Then** answer counts

#### INV-36 — added in step 3: A Cook never answers their own request
- **Slice / command:** B3-S02 · Provide Help · **kind:** other (applies to every board)
- **Guard / outcome:** OwnRequest — the requester cannot provide Help to their own request
- **Sources:** board 'Cook cannot answer their own request' (B1–B4); decision D3; example added by the user 2026-10-31
  - `US-CA-03 S3.E1` **Given/When** Lena tries to answer her own request about step 4 **Then** refused, not her own request

#### INV-37 — added in step 3: An answer must match its request (same type, same recipe and step / ingredient)
- **Slice / command:** B3-S02 · Provide Help · **kind:** other (B2: same ingredient; B4: same recipe if one was given)
- **Guard / outcome:** AnswerDoesNotMatchRequest
- **Sources:** Help model 'Must be the same as request type', 'Same recipe as in request', 'Same howTo as in request', 'Same ingredientId as in request'; example added by the user 2026-10-31
  - `US-CA-03 S3.E2` **Given/When** Grandma answers Lena's step-4 request with an explanation of step 6 **Then** refused, the answer must be about step 4

#### INV-38 — added in step 3: At most 10 pictures
- **Slice / command:** B3-S02 · Provide Help (answer pictures); B4-S01 · Request help Catastrophe (request photos) · **kind:** cardinality
- **Guard / outcome:** TooManyPictures — 0..10 pictures
- **Sources:** glossary v2 'Help Request contains Picture 0..10' (chosen over model v2 0..*); example added by the user 2026-10-31
  - `US-CA-03 S3.E3` **Given/When** Grandma's answer carries 11 photos **Then** refused, max 10

#### Per-provider state (decision, step 3)
Each asked provider has its own deadline. A provider's side times out alone, and the request times out when every asked provider has (US-CA-04 R9 E3, E4). The B3 examples stay valid, because both sides share the same 12 h. Answers `Q_EM_PerProviderState`.

## B2 — Help with ingredient substitution

### US-CA-02

#### INV-10 — US-CA-02 R1: Help with Ingredients goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask
- **Slice / command:** B2-S01 · Request help ingredients · **kind:** cardinality
- **Guard / outcome:** as INV-01 for type Ingredient Substitute
- **Sources:** map; board 'No Chef support for ingredient substitution', 'Help Substitutions can be provided by community, or Grandma'
  - `US-CA-02 R1.E1` **Given/When** Lena asks Grandma for a crème fraîche substitute **Then** Grandma receives it
  - `US-CA-02 R1.E2` **Given/When** Lena asks the Community and Grandma together **Then** both get the same request
  - `US-CA-02 R1.E3` **Given/When** Tom (free plan) asks the Community **Then** request sent
  - `US-CA-02 R1.E4` **Given/When** Lena looks for help providers **Then** Chef Marco not offered

#### INV-11 — US-CA-02 R2: A request covers one ingredient in any recipe, with its substitute requirements
- **Slice / command:** B2-S01 · Request help ingredients · **kind:** cardinality
- **Guard / outcome:** exactly one ingredient of one recipe, with its substitute requirements; two ingredients → two requests
- **Sources:** map; contradicts glossary 'ingredients 0..*' (graph R_EM02_R2) → map wins for this type; model 'recipe mandatory', 'ingredients only for Ingredient Substitute'
  - `US-CA-02 R2.E1` **Given/When** 200 g crème fraîche in tomato soup, shop has none **Then** shows recipe, amount, "not available"
  - `US-CA-02 R2.E2` **Given/When** Bacon in carbonara for a vegetarian guest **Then** shows "vegetarian"
  - `US-CA-02 R2.E3` **Given/When** Crème fraîche and bacon **Then** two requests

#### INV-12 — US-CA-02 R3: An answer can propose several substitutes, each one ingredient or a mix with amounts; accepting one closes the whole request
- **Slice / command:** B2-S02 · Provide Help · **kind:** cardinality
- **Guard / outcome:** an answer proposes 1..* substitutes, each one ingredient or a mix with amounts
- **Sources:** map R3 (E1–E3); glossary substitutes 1..*, substitute ingredient Name/Unit/Value
  - `US-CA-02 R3.E1` **Given/When** Grandma proposes yogurt, quark, sour cream; Lena takes yogurt **Then** closed
  - `US-CA-02 R3.E2` **Given/When** Jonas proposes yogurt, Mia mascarpone; Lena takes Jonas's **Then** closed
  - `US-CA-02 R3.E3` **Given/When** Grandma proposes "100 g mascarpone + 100 g yogurt" or 200 g quark; Lena takes the mix **Then** closed, mix with amounts accepted

#### INV-13 — US-CA-02 R3: An answer can propose several substitutes, each one ingredient or a mix with amounts; accepting one closes the whole request
- **Slice / command:** B3-S04 · Accept help · **kind:** other
- **Guard / outcome:** accepting one substitute closes the whole request (extends INV-05); what is cooked afterwards is not tracked
- **Sources:** map R3 (E1–E5)
  - `US-CA-02 R3.E4` **Given/When** Lena accepted the mix but cooks with mascarpone only **Then** stays closed; what she cooks is her choice
  - `US-CA-02 R3.E5` **Given/When** Both asked, Lena accepts Grandma's quark **Then** closed for Community too

#### INV-14 — US-CA-02 R4: Not accepted: the request stays open and goes to the same providers again, new 24 h deadline
- **Slice / command:** B3-S05 · Reject help · **kind:** quantitative
- **Guard / outcome:** not accepted: stays open, same providers, new deadline = + 24 h
- **Sources:** map (photo; graph mislinks to US-CA-03 R5)
  - `US-CA-02 R4.E1` **Given/When** Lena doesn't accept Grandma's sour cream, Mon 11:00 **Then** sent again, deadline Tue 11:00
  - `US-CA-02 R4.E2` **Given/When** Jonas: "no good substitute for saffron" **Then** not accepted, stays open
  - `US-CA-02 R4.E3` **Given/When** Both asked, nothing accepted **Then** sent again to both

#### INV-15 — US-CA-02 R5: Rejected substitutes are kept as notes and not proposed again, now or later
- **Slice / command:** B2-S02 / B3-S05 · Provide Help · **kind:** other
- **Guard / outcome:** rejected substitutes are kept as notes and are not proposed again in this request (guard) nor later (read model across requests)
- **Sources:** map
  - `US-CA-02 R5.E1` **Given/When** Lena rejects mascarpone, too heavy **Then** note "mascarpone – too heavy"
  - `US-CA-02 R5.E2` **Given/When** Request sent again **Then** Community sees note, no mascarpone
  - `US-CA-02 R5.E3` **Given/When** Next week: crème fraîche in mushroom sauce **Then** note carried over, no mascarpone

#### INV-16 — US-CA-02 R6: The Cook can cancel an open request at any time, with an optional note
- **Slice / command:** B3-S03 · Cancel help request · **kind:** other
- **Guard / outcome:** = INV-08
- **Sources:** map (same card)
  - `US-CA-02 R6.E1` **Given/When** Lena cancels: "found it at my neighbour's" **Then** closed, providers see the note
  - `US-CA-02 R6.E2` **Given/When** Lena cancels without a note **Then** closed

#### INV-17 — US-CA-02 R7: Unanswered after 24 h: cancelled; a provider not yet asked is suggested
- **Slice / command:** B3-S06 · Time out help request · **kind:** quantitative
- **Guard / outcome:** no answer within 24 h → timed out; suggest a provider not yet asked
- **Sources:** map; red card Q1 deferred → increment 2
  - `US-CA-02 R7.E1` **Given/When** Only Community asked Mon 18:00, silent until Tue 18:00 **Then** cancelled, Grandma suggested
  - `US-CA-02 R7.E2` **Given/When** Only Grandma asked Mon 10:00, silent until Tue 10:00 **Then** cancelled, Community suggested
  - `US-CA-02 R7.E3` **Given/When** Both asked, both silent 24 h **Then** cancelled, Lena told
  - `US-CA-02 R7.E4` **Given/When** Jonas answers Tue 17:59 **Then** answer counts

## B4 — Help with a catastrophe

### US-CA-04

#### INV-18 — US-CA-04 R1: Catastrophe help goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask
- **Slice / command:** B4-S01 · Request help Catastrophe · **kind:** cardinality
- **Guard / outcome:** as INV-01 for type Steps to Mitigate Catastrophe
- **Sources:** map (photo; graph mislinks to US-CA-03 R1); board 'No Chef support … Catastrophe'
  - `US-CA-04 R1.E1` **Given/When** Sat 18:40, Lena asks Grandma about her burnt roast chicken **Then** Grandma receives it
  - `US-CA-04 R1.E2` **Given/When** Lena asks the Community and Grandma together **Then** both get the same request
  - `US-CA-04 R1.E3` **Given/When** Tom (free plan) asks about his collapsed soufflé **Then** request sent
  - `US-CA-04 R1.E4` **Given/When** Lena looks for help providers **Then** Chef Marco not offered

#### INV-19 — US-CA-04 R2: A request describes one catastrophe with one dish; a recipe can be given
- **Slice / command:** B4-S01 · Request help Catastrophe · **kind:** cardinality
- **Guard / outcome:** one catastrophe, one dish; recipe optional
- **Sources:** map; board arrow 'can be provided not mandatory'; glossary recipe 0..1
  - `US-CA-04 R2.E1` **Given/When** "Roast chicken burnt on the bottom, guests at 19:00" **Then** shows dish and what happened
  - `US-CA-04 R2.E2` **Given/When** Lena adds her recipe "Roast chicken" **Then** recipe shown
  - `US-CA-04 R2.E3` **Given/When** Lena gives no recipe **Then** sent without recipe
  - `US-CA-04 R2.E4` **Given/When** The rice burns too **Then** second request

#### INV-20 — US-CA-04 R3: A catastrophe request can contain photos
- **Slice / command:** B4-S01 · Request help Catastrophe · **kind:** cardinality
- **Guard / outcome:** the request may carry photos (upper bound open: glossary v2 0..10 vs model v2 0..*)
- **Sources:** map; glossary
  - `US-CA-04 R3.E1` **Given/When** Lena adds a photo of the burnt bottom **Then** providers see the photo

#### INV-21 — US-CA-04 R4: Community members are notified of every request sent to the Community
- **Slice / command:** policy → Notification · (Help requested published) · **kind:** other
- **Guard / outcome:** every request sent to the Community notifies Community members — cross-context policy, staleness: seconds; compensation: none (notification only)
- **Sources:** map; board 'Cook notified'; red card Q1 deferred → increment 3
  - `US-CA-04 R4.E1` **Given/When** Lena asks the Community at 18:40 **Then** Jonas and Mia notified at 18:40

#### INV-22 — US-CA-04 R5: Accepting an answer closes the whole request
- **Slice / command:** B3-S04 · Accept help · **kind:** other
- **Guard / outcome:** = INV-05
- **Sources:** map
  - `US-CA-04 R5.E1` **Given/When** Grandma: "cut off the burnt part, make a fresh sauce"; Lena accepts 18:44 **Then** closed
  - `US-CA-04 R5.E2` **Given/When** Both asked; Lena accepts Jonas's answer 18:45 **Then** closed for Grandma too

#### INV-23 — US-CA-04 R6: Not accepted: sent again to the same providers with a note and new deadlines
- **Slice / command:** B3-S05 · Reject help · **kind:** quantitative
- **Guard / outcome:** resend with a note; new deadline per provider: Community + 15 min, Grandma + 5 min
- **Sources:** map
  - `US-CA-04 R6.E1` **Given/When** 18:47 Lena: "still tastes smoky" **Then** sent again to Grandma, deadline 18:52
  - `US-CA-04 R6.E2` **Given/When** Both asked, nothing accepted 18:47 **Then** Community until 19:02, Grandma until 18:52

#### INV-24 — US-CA-04 R7: Notes are kept and shown on future requests
- **Slice / command:** read model · (notes) · **kind:** other
- **Guard / outcome:** notes are shown on future catastrophe requests — as INV-07
- **Sources:** map
  - `US-CA-04 R7.E1` **Given/When** Next month: burnt roast chicken again **Then** note "still tastes smoky" shown

#### INV-25 — US-CA-04 R8: The Cook can cancel an open request at any time, with an optional note
- **Slice / command:** B3-S03 · Cancel help request · **kind:** other
- **Guard / outcome:** = INV-08
- **Sources:** map (same card)
  - `US-CA-04 R8.E1` **Given/When** 18:50 Lena cancels: "ordered pizza" **Then** closed, providers see the note

#### INV-26 — US-CA-04 R9: Unanswered: cancelled after 15 min (Community) or 5 min (Grandma); a provider not yet asked is suggested
- **Slice / command:** B3-S06 · Time out help request · **kind:** quantitative
- **Guard / outcome:** deadline per provider (Community 15 min, Grandma 5 min); a provider's side times out alone; the request times out when every asked provider has; suggest a provider not yet asked
- **Sources:** map (E3 shows per-provider state)
  - `US-CA-04 R9.E1` **Given/When** Only Community asked 18:40, silent until 18:55 **Then** cancelled, Grandma suggested
  - `US-CA-04 R9.E2` **Given/When** Only Grandma asked 18:40, silent until 18:45 **Then** cancelled, Community suggested
  - `US-CA-04 R9.E3` **Given/When** Both asked, Grandma silent at 18:45 **Then** only Grandma's side cancelled, Community until 18:55
  - `US-CA-04 R9.E4` **Given/When** Both asked, both silent until 18:55 **Then** cancelled, Lena told
  - `US-CA-04 R9.E5` **Given/When** Jonas answers 18:54 **Then** answer counts

## B1 — Help with a meal plan

### US-CA-01a

#### INV-27 — US-CA-01 R1: A request covers exactly one Occasion
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** cardinality
- **Guard / outcome:** exactly one Occasion (date, guests, dietary needs, budget, tastes)
- **Sources:** map
  - `US-CA-01 R1.E1` **Given/When** Lena asks Marco for a Menu for "Mother-in-law visiting", Sat 19:00, 4 guests, vegetarian, 60 € **Then** Marco gets the Occasion, no courses
  - `US-CA-01 R1.E2` **Given/When** Lena needs Menus for Saturday dinner and Sunday lunch **Then** two separate requests

#### INV-28 — US-CA-01 R3: Only a premium Cook can request; an open request closes when premium ends
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** other
- **Guard / outcome:** NotPremium — only a premium Cook can request; an open request closes when premium ends (cross-context policy from Cook Profile/Paywall)
- **Sources:** map; board 'Only premium users allowed to ask Chef', 'Chef can only be asked when cook is premium user.'
  - `US-CA-01 R3.E1` **Given/When** Tom (free plan) asks Marco **Then** no request, upgrade offered
  - `US-CA-01 R3.E2` **Given/When** Lena's premium ended yesterday **Then** no request, upgrade offered
  - `US-CA-01 R3.E3` **Given/When** Lena's premium ends Tuesday, request open **Then** closed Tuesday, Marco told

#### INV-29 — US-CA-01 R4: A Cook picks a Chef by name and can have several open requests
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** cardinality
- **Guard / outcome:** one Chef picked by name per request; several open requests allowed; accepting one does not close the others
- **Sources:** map; board 'Chef needs to be selected'; SADR0001 Chef exclusive; decision D2
  - `US-CA-01 R4.E1` **Given/When** Lena picks Marco **Then** only Marco receives it
  - `US-CA-01 R4.E2` **Given/When** Lena asks Marco and Ana, accepts Marco's Menu **Then** Marco's closed, Ana's stays open
  - `US-CA-01 R4.E3` **Given/When** Lena closes Ana's request herself **Then** closed, Ana told

#### INV-30 — US-CA-01 R5: Only Chefs who can answer in time can be selected, in their own time zone
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** other
- **Guard / outcome:** ChefUnavailable — only Chefs who can answer before the deadline, in their own time zone (needs Chef availability: cross-context)
- **Sources:** map
  - `US-CA-01 R5.E1` **Given/When** Deadline Fri 19:00 CET; Sam in New York works Thu until 18:00 local **Then** Sam is listed
  - `US-CA-01 R5.E2` **Given/When** Sam is away until Saturday **Then** Sam is not listed

#### INV-31 — US-CA-01 R9: A new request needs at least 48 h before the Occasion
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** quantitative
- **Guard / outcome:** OccasionTooClose — a new request needs ≥ 48 h before the Occasion
- **Sources:** map
  - `US-CA-01 R9.E1` **Given/When** Asked Thu 19:00 for Sat 19:00 **Then** sent, deadline Fri 19:00
  - `US-CA-01 R9.E2` **Given/When** Asked Thu 20:00 for Sat 19:00 **Then** refused, 48 h needed

### US-CA-01b

#### INV-32 — US-CA-01 R2: The Chef proposes the courses and a recipe for each; Chefs never handle substitutions
- **Slice / command:** B1-S02 · Provide Help · **kind:** cardinality
- **Guard / outcome:** the Chef proposes the courses and a recipe for each (1..n courses; glossary 1..10 — upper bound unconfirmed); Chefs never handle substitutions
- **Sources:** map; glossary course 1..10; red card Q1 deferred → increment 4
  - `US-CA-01 R2.E1` **Given/When** Marco proposes starter, main and dessert **Then** 3 courses, 3 recipes
  - `US-CA-01 R2.E2` **Given/When** Marco proposes main and dessert only **Then** valid 2-course Menu
  - `US-CA-01 R2.E3` **Given/When** Lena asks Marco for a crème fraîche substitute **Then** nothing goes to Marco; separate request to Community or Grandma Avatar

#### INV-33 — US-CA-01 R6: A Chef can review any Menu from the Grandma Avatar
- **Slice / command:** B1-S01 · Request help meal plan · **kind:** other
- **Guard / outcome:** a Chef can review any Menu from the Grandma Avatar (Grandma's Menu attached); no Grandma Menu → no review option
- **Sources:** map; board 'Chef can review a dinner plan of Grandma Avartar'
  - `US-CA-01 R6.E1` **Given/When** Grandma proposed "Lasagne and salad"; Lena asks Marco to review **Then** Marco gets it with Grandma's Menu attached
  - `US-CA-01 R6.E2` **Given/When** Lena has no Grandma Menu **Then** review option not offered

#### INV-34 — US-CA-01 R7: An Answer is the complete Menu; the Cook accepts, rejects or leaves it
- **Slice / command:** B3-S04 / B3-S05 / B3-S03 · Accept help / Reject help / Cancel · **kind:** other
- **Guard / outcome:** an Answer is the complete Menu; the Cook accepts (Closed), rejects (stays open, the Chef may answer again; may pass to another available Chef who doesn't see the first Menu) or leaves it (nothing expires once answered)
- **Sources:** map; red card Q2 deferred → increment 4
  - `US-CA-01 R7.E1` **Given/When** Lena accepts Marco's Menu **Then** request closed
  - `US-CA-01 R7.E2` **Given/When** Lena never responds **Then** Menu stays with her, nothing expires
  - `US-CA-01 R7.E3` **Given/When** Lena rejects Wed 18:00 **Then** stays open; Marco may answer again until Fri 18:00
  - `US-CA-01 R7.E4` **Given/When** Lena rejects and passes it to Ana **Then** Ana gets it, doesn't see Marco's Menu
  - `US-CA-01 R7.E5` **Given/When** Lena tries to pass it to Sam, away next week **Then** Sam not offered
  - `US-CA-01 R7.E6` **Given/When** Lena rejects Fri 21:00, Occasion Sat 19:00 **Then** deadline Sun 21:00, note "22 h to the Occasion"
  - `US-CA-01 R7.E7` **Given/When** Lena cancels before any answer **Then** withdrawn, Marco told

### US-CA-01c

#### INV-35 — US-CA-01 R8: First request times out after 48 h or 24 h before the Occasion, whichever is earlier; every reset gives 48 h
- **Slice / command:** B3-S06 / B3-S05 · Time out help request · **kind:** quantitative
- **Guard / outcome:** first deadline = min(asked + 48 h, Occasion − 24 h); every reset gives 48 h; an answer before the deadline counts
- **Sources:** map; red card Q2 deferred → increment 4
  - `US-CA-01 R8.E1` **Given/When** Asked Mon 18:00 **Then** deadline Wed 18:00
  - `US-CA-01 R8.E2` **Given/When** Asked Thu 10:00 **Then** deadline Fri 19:00
  - `US-CA-01 R8.E3` **Given/When** Marco silent until Wed 18:00 **Then** times out, Ana suggested
  - `US-CA-01 R8.E4` **Given/When** Lena takes Ana Wed 18:00 **Then** Ana's deadline Fri 18:00
  - `US-CA-01 R8.E5` **Given/When** Lena takes Ana Fri 19:00 **Then** deadline Sun 19:00, note "24 h to the Occasion"
  - `US-CA-01 R8.E6` **Given/When** Marco answers Wed 17:58 **Then** answer counts, no timeout

## Open questions (red cards and graph questions)

| ID | Question | Rule | Deferred to |
|---|---|---|---|
| US-CA-03 Q1 | Notes shown only for the same step of the same recipe, or for any request about the same technique? | INV-07 | increment 1 readiness gate |
| US-CA-02 Q1 | If both providers stay silent, should Lena be offered anything? | INV-17 | increment 2 |
| B2 pictures | May an ingredient request or answer carry pictures? (map silent) | INV-11/12 | increment 2 |
| US-CA-04 Q1 | Is the Community also notified when only Grandma was asked? | INV-21 | increment 3 |
| US-CA-01 Q1 | Must the Chef's Menu respect the Occasion's dietary needs and budget? | INV-32 | increment 4 |
| US-CA-01 Q2 | May a reset push the deadline past the Occasion? | INV-34, INV-35 | increment 4 |
| Course bound | Menu course 1..10 (glossary) — upper bound unconfirmed by the map | INV-32 | increment 4 (example needed) |
| Q_EM_OccasionInHelpRequest | Where does the Occasion live on a Menu help request? | INV-27 | increment 4 |
| Q_EM_RejectVsCancelWords | closed / cancelled / withdrawn / times out | — | answered by step 2: Closed (accepted), Cancelled (cancel or timeout; event Help request timed out). The maps' "closed" after cancelling = status Cancelled |
| Q_EM_PicturesPerType | Which kinds of request may carry pictures? | INV-03, 20, 38 | answered: B3 no, B4 yes (≤ 10), B1 not stated, B2 open (above) |
| Graph defect | US-CA-02 R1/R4 and US-CA-04 R1 examples linked to US-CA-03 rule nodes | — | sheet uses the photo wording; fix in the graph at step 15 |

## Policies and read models (not aggregate guards)
- INV-07, INV-24, and the "later" part of INV-15: notes across requests → read model / query (step 11)
- INV-21: Community notification → published event, Notification reacts (steps 10 and 12)
- INV-28: closing when premium ends → policy on a Cook Profile / Paywall event (step 8, increment 4)
- INV-30: Chef availability → query of another context (step 5 decides how)
