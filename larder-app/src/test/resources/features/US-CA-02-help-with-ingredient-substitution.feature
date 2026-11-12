# Board B2 — Help with ingredient substitution. Rules and examples one to one from the Example Mapping
# (docs/cooking-assistance/design/invariants.md, US-CA-02). @wip: not active yet; @planned: later increment.
@B2 @US-CA-02
Feature: As a Cook, I want to ask a help provider for Help with Ingredients, so that I can still cook when an ingredient is missing or doesn't suit my guests.

  Rule: R1 — Help with Ingredients goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask

    @B2-S01 @INV-10 @R1.E1
    Scenario: Lena asks Grandma for a crème fraîche substitute
      When Lena asks Grandma Avatar for a substitute for the crème fraîche in her tomato soup
      Then Grandma Avatar receives the request about the crème fraîche

    @B2-S01 @INV-10 @R1.E2
    Scenario: Lena asks the Community and Grandma together
      When Lena asks the Community and Grandma Avatar for a substitute for the crème fraîche
      Then the Community and Grandma Avatar receive the same ingredient request

    @B2-S01 @INV-10 @R1.E3
    Scenario: Tom on the free plan asks the Community
      When Tom, on the free plan, asks the Community for a substitute for an ingredient
      Then the ingredient request is sent to the Community

    @B2-S01 @INV-10 @R1.E4
    Scenario: Asking chef Marco is refused
      When Lena asks chef Marco for a substitute for the crème fraîche
      Then the ingredient request is refused
      And no ingredient request is sent

  Rule: R2 — A request covers one ingredient in any recipe, with its substitute requirements

    @B2-S01 @INV-11 @R2.E1
    Scenario: 200 g crème fraîche in tomato soup, the shop has none
      When Lena asks for a substitute for "200 g, not available" crème fraîche in her tomato soup
      Then her ingredient request shows the tomato soup, the crème fraîche and "200 g, not available"

    @B2-S01 @INV-11 @R2.E2
    Scenario: Bacon in carbonara for a vegetarian guest
      When Lena asks for a substitute for the bacon in her carbonara: "vegetarian guest"
      Then her ingredient request shows "vegetarian guest"

    @B2-S01 @INV-11 @R2.E3
    Scenario: Crème fraîche and bacon mean two requests
      When Lena asks for a substitute for the crème fraîche and for the bacon
      Then she has two ingredient requests, one about the crème fraîche and one about the bacon

  Rule: R3 — An answer can propose several substitutes, each one ingredient or a mix with amounts; accepting one closes the whole request

    @B2-S02 @B3-S04 @INV-12 @R3.E1
    Scenario: Grandma proposes yogurt, quark and sour cream; Lena takes yogurt
      Given Lena asked Grandma Avatar for a substitute for the crème fraîche
      When Grandma Avatar proposes yogurt, quark and sour cream
      And Lena takes Grandma's yogurt
      Then her ingredient request is closed

    @B2-S02 @B3-S04 @INV-12 @R3.E2
    Scenario: Jonas proposes yogurt, Mia mascarpone; Lena takes Jonas's
      Given Lena asked the Community for a substitute for the crème fraîche
      When Jonas proposes yogurt and Mia proposes mascarpone
      And Lena takes Jonas's yogurt
      Then her ingredient request is closed

    @B2-S02 @B3-S04 @INV-12 @R3.E3
    Scenario: Grandma proposes a mix or quark; Lena takes the mix
      Given Lena asked Grandma Avatar for a substitute for the crème fraîche
      When Grandma Avatar proposes "100 g mascarpone + 100 g yogurt" or 200 g quark
      And Lena takes Grandma's mix
      Then her ingredient request is closed with the mix of 100 g mascarpone and 100 g yogurt accepted

    @B3-S04 @INV-13 @R3.E4
    Scenario: Lena accepted the mix but cooks with mascarpone only
      Given Lena took Grandma's mix for the crème fraîche
      When she cooks with mascarpone only
      Then her ingredient request stays closed

    @B3-S04 @INV-13 @R3.E5
    Scenario: Both asked, Lena accepts Grandma's quark
      Given Lena asked the Community and Grandma Avatar for a substitute for the crème fraîche
      When Grandma Avatar proposes quark
      And Lena takes Grandma's quark
      Then her ingredient request is closed for the Community too

  Rule: R4 — Not accepted: the request stays open and goes to the same providers again, new 24 h deadline

    @B3-S05 @INV-14 @R4.E1
    Scenario: Lena doesn't accept Grandma's sour cream on Monday at 11:00
      Given Lena asked Grandma Avatar for a substitute for the crème fraîche
      And Grandma Avatar proposed sour cream
      When on Monday at 11:00 Lena rejects the sour cream: "too sour"
      Then the ingredient request is sent again to Grandma with the deadline Tuesday 11:00

    @B3-S05 @INV-14 @R4.E2
    Scenario: Jonas finds no good substitute for saffron
      Given Lena asked the Community for a substitute for the saffron in her risotto
      When Jonas answers that there is no good substitute for saffron
      And Lena rejects Jonas's answer
      Then her ingredient request stays open

    @B3-S05 @INV-14 @R4.E3
    Scenario: Both asked, nothing accepted
      Given Lena asked the Community and Grandma Avatar for a substitute for the crème fraîche
      And Grandma Avatar proposed sour cream
      When Lena rejects the sour cream: "too sour"
      Then the ingredient request is sent again to both

  Rule: R5 — Rejected substitutes are kept as notes and not proposed again, now or later

    @B3-S05 @INV-15 @R5.E1
    Scenario: Lena rejects mascarpone, too heavy
      Given Lena asked Grandma Avatar for a substitute for the crème fraîche
      And Grandma Avatar proposed mascarpone
      When Lena rejects the mascarpone: "mascarpone – too heavy"
      Then her ingredient request keeps the note "mascarpone – too heavy" on Grandma's answer

    @B2-S02 @B3-S05 @INV-15 @R5.E2
    Scenario: The request sent again shows the note and keeps mascarpone out
      Given Lena asked the Community and Grandma Avatar for a substitute for the crème fraîche
      And Lena rejected Grandma's mascarpone: "mascarpone – too heavy"
      When the Community reads her ingredient request
      Then the Community sees the note "mascarpone – too heavy"
      And Jonas is refused when he proposes mascarpone

    @B3-S05 @INV-15 @R5.E3
    Scenario: Next week, crème fraîche in mushroom sauce
      Given Lena rejected Grandma's mascarpone for the crème fraîche in her tomato soup: "mascarpone – too heavy"
      When next week she asks for a substitute for the crème fraîche in her mushroom sauce
      Then her new ingredient request shows the earlier note "mascarpone – too heavy"

  Rule: R6 — The Cook can cancel an open request at any time, with an optional note

    @B3-S03 @INV-16 @R6.E1
    Scenario: Lena cancels: found it at her neighbour's
      Given Lena asked the Community for a substitute for the crème fraîche
      When she cancels her ingredient request: "found it at my neighbour's"
      Then her ingredient request is cancelled and the providers see the note "found it at my neighbour's"

    @B3-S03 @INV-16 @R6.E2
    Scenario: Lena cancels without a note
      Given Lena asked the Community for a substitute for the crème fraîche
      When she cancels her ingredient request without a note
      Then her ingredient request is cancelled

  Rule: R7 — Unanswered after 24 h: cancelled; a provider not yet asked is suggested

    @B3-S06 @INV-17 @R7.E1
    Scenario: Only the Community asked on Monday at 18:00, silent until Tuesday at 18:00
      Given Lena asked only the Community for a substitute on Monday at 18:00
      When nobody answers until Tuesday at 18:00
      Then her ingredient request is cancelled and Grandma Avatar is suggested

    @B3-S06 @INV-17 @R7.E2
    Scenario: Only Grandma asked on Monday at 10:00, silent until Tuesday at 10:00
      Given Lena asked only Grandma Avatar for a substitute on Monday at 10:00
      When nobody answers until Tuesday at 10:00
      Then her ingredient request is cancelled and the Community is suggested

    @B3-S06 @INV-17 @R7.E3
    Scenario: Both asked, both silent for 24 hours
      Given Lena asked the Community and Grandma Avatar for a substitute on Monday at 18:00
      When nobody answers until Tuesday at 18:00
      Then her ingredient request is cancelled and Lena is told

    @B3-S06 @INV-17 @R7.E4
    Scenario: Jonas answers on Tuesday at 17:59
      Given Lena asked only the Community for a substitute on Monday at 18:00
      When Jonas proposes yogurt on Tuesday at 17:59
      Then his proposal counts
