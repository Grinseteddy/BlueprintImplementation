# Board B3 — Help with a preparation step. Rules and examples one to one from the Example Mapping
# (docs/cooking-assistance/design/invariants.md, US-CA-03). @wip: not active yet; @planned: later increment.
@B3 @US-CA-03
Feature: As a Cook, I want to ask a help provider for Help with a Meal Preparation Step, so that I can complete a preparation step I'm unsure about.

  Rule: R1 — Help with a preparation step goes to the Community and/or Grandma Avatar, never to a Chef; every Cook can ask

    @B3-S01 @INV-01 @R1.E1
    Scenario: Lena asks Grandma about step 4 of her Hollandaise
      When Lena asks Grandma Avatar about step 4 of her Hollandaise
      Then Grandma Avatar receives the request about step 4

    @B3-S01 @INV-01 @R1.E2
    Scenario: Lena asks the Community and Grandma together
      When Lena asks the Community and Grandma Avatar about step 4 of her Hollandaise
      Then the Community and Grandma Avatar receive the same request

    @B3-S01 @INV-01 @R1.E3
    Scenario: Tom on the free plan asks about step 2 of his bread
      When Tom, on the free plan, asks the Community about step 2 of his bread
      Then the request is sent to the Community

    @B3-S01 @INV-01 @R1.E4
    Scenario: Marco, the chef who proposed the Hollandaise, is not offered
      When Lena asks chef Marco about step 4 of her Hollandaise
      Then the request is refused because a chef only helps with a menu proposal
      And nobody receives a request

  Rule: R2 — A request can only have one step: one numbered step of one recipe

    @B3-S01 @INV-02 @R2.E1
    Scenario: Lena's request shows the recipe, the step and her question
      When Lena asks the Community "How do I keep the sauce from splitting?" about step 4 of her Hollandaise
      Then her request shows the Hollandaise, step 4 and her question

    @B3-S01 @INV-02 @R2.E2
    Scenario: Unsure about steps 4 and 6 means two requests
      When Lena asks the Community about step 4 and about step 6 of her Hollandaise
      Then she has two requests, one about step 4 and one about step 6

    @B3-S01 @INV-02 @R2.E3
    Scenario: Lena cannot add step 6 to her open request on step 4
      Given Lena asked the Community about step 4 of her Hollandaise
      When she tries to add step 6 to that request
      Then nothing is added: the request is still about step 4 only

  Rule: R3 — A request is text only

    @B3-S01 @INV-03 @R3.E1
    Scenario: A photo cannot be attached to the request
      When Lena asks the Community about step 4 with a photo of the split sauce
      Then the request with the photo is refused
      But the same request without the photo is accepted

  Rule: R4 — An answer may include pictures; accepting an answer closes the whole request

    @B3-S02 @INV-04 @R4.E1
    Scenario: Grandma answers with three photos
      Given Lena asked Grandma Avatar about step 4 of her Hollandaise
      When Grandma Avatar answers "butter in a thin stream" with 3 photos
      Then the answer with its 3 photos is announced
      And Lena's request is answered

    @B3-S02 @INV-04 @R4.E3
    Scenario: Jonas and Mia both answer
      Given Lena asked the Community about step 4 of her Hollandaise
      When Jonas and Mia from the Community both answer
      Then both answers are announced
      And Lena's request is answered

    @B3-S04 @INV-05 @R4.E3 @planned
    Scenario: Lena accepts Mia's answer
      Given Jonas and Mia answered Lena's request about step 4
      When Lena accepts Mia's answer
      Then the request is closed

    @B3-S04 @INV-05 @R4.E2 @planned
    Scenario: Lena accepts Grandma's answer
      Given Grandma Avatar answered Lena's request about step 4
      When Lena accepts Grandma's answer
      Then the request is closed

    @B3-S04 @INV-05 @R4.E4 @planned
    Scenario: Accepting Grandma's answer closes the request for the Community too
      Given Lena asked the Community and Grandma Avatar and Grandma answered
      When Lena accepts Grandma's answer
      Then the request is closed for the Community too

  Rule: R5 — Not accepted: sent again to the same providers with a note on what didn't work, new 12 h deadline

    @B3-S05 @INV-06 @R5.E1 @planned
    Scenario: Lena rejects Grandma's answer with a note
      Given Grandma Avatar answered Lena's request about step 4
      When on Friday at 20:00 Lena rejects it: "warm water didn't help"
      Then the request is sent again to Grandma with the note and the deadline Saturday 08:00

    @B3-S05 @INV-06 @R5.E2 @planned
    Scenario: Nothing accepted while both were asked
      Given Lena asked the Community and Grandma Avatar and both answered
      When Lena accepts neither
      Then the request is sent again to both

  Rule: R6 — Notes are kept and shown on future requests for the same step

    @B3-S01 @INV-07 @R6.E1 @planned
    Scenario: Next month the note is shown again
      Given Lena noted "warm water didn't help" on step 4 of her Hollandaise
      When next month she asks about step 4 again
      Then the note "warm water didn't help" is shown

  Rule: R7 — The Cook can cancel an open request at any time, with an optional note

    @B3-S03 @INV-08 @R7.E1 @planned
    Scenario: Lena cancels with a note
      Given Lena asked the Community about step 4 of her Hollandaise
      When she cancels: "an ice cube saved it"
      Then the request is closed and the providers see the note

    @B3-S03 @INV-08 @R7.E2 @planned
    Scenario: Lena cancels without a note
      Given Lena asked the Community about step 4 of her Hollandaise
      When she cancels without a note
      Then the request is closed

  Rule: R8 — Unanswered after 12 h: cancelled; a provider not yet asked is suggested

    @B3-S06 @INV-09 @R8.E1 @planned
    Scenario: Only the Community was asked and stayed silent
      Given Lena asked only the Community on Friday at 18:00
      When nobody answers until Saturday 06:00
      Then the request is cancelled and Grandma Avatar is suggested

    @B3-S06 @INV-09 @R8.E2 @planned
    Scenario: Only Grandma was asked and stayed silent
      Given Lena asked only Grandma Avatar on Friday at 18:00
      When nobody answers until Saturday 06:00
      Then the request is cancelled and the Community is suggested

    @B3-S06 @INV-09 @R8.E3 @planned
    Scenario: Both were asked and both stayed silent
      Given Lena asked the Community and Grandma Avatar
      When both stay silent for 12 hours
      Then the request is cancelled and Lena is told

    @B3-S06 @INV-09 @R8.E4 @planned
    Scenario: An answer one minute before the deadline counts
      Given Lena asked the Community on Friday at 18:00
      When Jonas answers on Saturday at 05:59
      Then his answer counts

  Rule: S3 — A Cook never answers their own request; an answer matches its request; at most 10 pictures

    @B3-S02 @INV-36 @S3.E1
    Scenario: Lena cannot answer her own request
      Given Lena asked the Community about step 4 of her Hollandaise
      When Lena tries to answer her own request
      Then she is refused because it is her own request

    @B3-S02 @INV-37 @S3.E2
    Scenario: An answer about another step is refused
      Given Lena asked Grandma Avatar about step 4 of her Hollandaise
      When Grandma Avatar answers with an explanation of step 6
      Then Grandma's answer is refused and set aside
      And Lena's request is still open

    @B3-S02 @INV-38 @S3.E3
    Scenario: An answer with 11 photos is refused
      Given Lena asked Grandma Avatar about step 4 of her Hollandaise
      When Grandma Avatar answers with 11 photos
      Then Grandma's answer is refused and set aside
      And Lena's request is still open
