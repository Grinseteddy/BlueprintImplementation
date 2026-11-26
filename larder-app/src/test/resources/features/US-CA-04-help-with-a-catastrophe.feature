# Board B4 — Help with a catastrophe. Rules and examples one to one from the Example Mapping
# (docs/cooking-assistance/design/invariants.md, US-CA-04). @wip: not active yet; @planned: later increment.
# R4 (INV-21): the Community's notification is the Notification context's; this context's part is that
# HelpRequestCreated names the Community among the asked providers (step 3 r20, Q1).
@B4 @US-CA-04
Feature: As a Cook, I want to ask a help provider for help with my kitchen catastrophe, e.g. a burnt meal, so that I get help to rescue my meals and dinners.

  Rule: R1 — Catastrophe help goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask

    @B4-S01 @INV-18 @R1.E1
    Scenario: Saturday 18:40, Lena asks Grandma about her burnt roast chicken
      When on Saturday at 18:40 Lena asks Grandma Avatar about her burnt roast chicken
      Then Grandma Avatar receives the catastrophe request

    @B4-S01 @INV-18 @R1.E2
    Scenario: Lena asks the Community and Grandma together
      When Lena asks the Community and Grandma Avatar about her burnt roast chicken
      Then the Community and Grandma Avatar receive the same catastrophe request

    @B4-S01 @INV-18 @R1.E3
    Scenario: Tom on the free plan asks about his collapsed soufflé
      When Tom, on the free plan, asks the Community about his collapsed soufflé
      Then the catastrophe request is sent to the Community

    @B4-S01 @INV-18 @R1.E4
    Scenario: Asking chef Marco is refused
      When Lena asks chef Marco about her burnt roast chicken
      Then the catastrophe request is refused
      And no catastrophe request is sent

  Rule: R2 — A request describes one catastrophe with one dish; a recipe can be given

    @B4-S01 @INV-19 @R2.E1
    Scenario: Roast chicken burnt on the bottom, guests at 19:00
      When Lena asks for help: "Roast chicken burnt on the bottom, guests at 19:00"
      Then her catastrophe request shows the dish and what happened

    @B4-S01 @INV-19 @R2.E2
    Scenario: Lena adds her recipe
      When Lena asks for help with her burnt roast chicken and adds her recipe "Roast chicken"
      Then her catastrophe request shows the recipe "Roast chicken"

    @B4-S01 @INV-19 @R2.E3
    Scenario: Lena gives no recipe
      When Lena asks for help with her burnt roast chicken without a recipe
      Then her catastrophe request is sent without a recipe

    @B4-S01 @INV-19 @R2.E4
    Scenario: The rice burns too
      Given Lena asked for help with her burnt roast chicken
      When the rice burns too and Lena asks for help with it
      Then she has two catastrophe requests, one about the roast chicken and one about the rice

  Rule: R3 — A catastrophe request can contain photos

    @B4-S01 @INV-20 @INV-38 @R3.E1
    Scenario: Lena adds a photo of the burnt bottom
      When Lena asks for help with her burnt roast chicken and adds a photo of the burnt bottom
      Then the providers see the photo

  Rule: R4 — Community members are notified of every request sent to the Community

    @B4-S01 @INV-21 @R4.E1
    Scenario: Lena asks the Community at 18:40
      When on Saturday at 18:40 Lena asks the Community about her burnt roast chicken
      Then the request announced at 18:40 names the Community among the asked providers, for its members to be notified

  Rule: R5 — Accepting an answer closes the whole request

    @B4-S02 @B3-S04 @INV-22 @R5.E1
    Scenario: Grandma's mitigation accepted at 18:44
      Given Lena asked Grandma Avatar about her burnt roast chicken at 18:40
      When Grandma Avatar answers "cut off the burnt part, make a fresh sauce"
      And Lena accepts Grandma's answer at 18:44
      Then her catastrophe request is closed

    @B4-S02 @B3-S04 @INV-22 @R5.E2
    Scenario: Both asked; Lena accepts Jonas's answer at 18:45
      Given Lena asked the Community and Grandma Avatar about her burnt roast chicken at 18:40
      And Jonas answered from the Community
      When Lena accepts Jonas's answer at 18:45
      Then her catastrophe request is closed, for Grandma Avatar too

  Rule: R6 — Not accepted: sent again to the same providers with a note and new deadlines

    @B4-S02 @B3-S05 @INV-23 @R6.E1
    Scenario: 18:47 "still tastes smoky"
      Given Lena asked Grandma Avatar about her burnt roast chicken at 18:40
      And Grandma Avatar answered
      When Lena does not accept it at 18:47: "still tastes smoky"
      Then the request is sent again to Grandma Avatar with the note, deadline 18:52

    @B4-S02 @B3-S05 @INV-23 @R6.E2
    Scenario: Both asked, nothing accepted at 18:47
      Given Lena asked the Community and Grandma Avatar about her burnt roast chicken at 18:40
      And Grandma Avatar answered
      When Lena does not accept it at 18:47: "still tastes smoky"
      Then the Community has until 19:02 and Grandma Avatar until 18:52

  Rule: R7 — Notes are kept and shown on future requests

    @B4-S01 @INV-24 @R7.E1
    Scenario: Next month, burnt roast chicken again, with the recipe
      Given Lena's earlier catastrophe request with the recipe "Roast chicken" carries the note "still tastes smoky"
      When next month Lena asks for help with her burnt roast chicken again, with the recipe "Roast chicken"
      Then her new request shows the note "still tastes smoky"

  Rule: R8 — The Cook can cancel an open request at any time, with an optional note

    @B3-S03 @INV-25 @R8.E1
    Scenario: 18:50 Lena cancels: "ordered pizza"
      Given Lena asked the Community about her burnt roast chicken at 18:40
      When Lena cancels at 18:50: "ordered pizza"
      Then her catastrophe request is cancelled
      And the providers see the note "ordered pizza"

  Rule: R9 — Unanswered: cancelled after 15 min (Community) or 5 min (Grandma); a provider not yet asked is suggested

    @B3-S06 @INV-26 @R9.E1
    Scenario: Only the Community asked at 18:40, silent until 18:55
      Given Lena asked only the Community about her burnt roast chicken at 18:40
      When nobody answers until 18:55
      Then her catastrophe request is cancelled
      And Grandma Avatar is suggested

    @B3-S06 @INV-26 @R9.E2
    Scenario: Only Grandma asked at 18:40, silent until 18:45
      Given Lena asked only Grandma Avatar about her burnt roast chicken at 18:40
      When nobody answers until 18:45
      Then her catastrophe request is cancelled
      And the Community is suggested

    @B3-S06 @INV-26 @R9.E3
    Scenario: Both asked, Grandma silent at 18:45
      Given Lena asked the Community and Grandma Avatar about her burnt roast chicken at 18:40
      When Grandma Avatar has not answered by 18:45
      Then only Grandma Avatar's side is cancelled
      And the Community still has until 18:55

    @B3-S06 @INV-26 @R9.E4
    Scenario: Both asked, both silent until 18:55
      Given Lena asked the Community and Grandma Avatar about her burnt roast chicken at 18:40
      When nobody answers until 18:55
      Then her catastrophe request is cancelled
      And Lena is told

    @B3-S06 @INV-26 @R9.E5
    Scenario: Jonas answers at 18:54
      Given Lena asked the Community about her burnt roast chicken at 18:40
      When Jonas answers at 18:54
      Then Jonas's answer counts
