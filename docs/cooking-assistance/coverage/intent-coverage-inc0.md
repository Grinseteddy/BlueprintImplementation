| Kind | Item | Passing tests | Levels |
|---|---|---|---|
| user story | US-CA-03 | 212 | acceptance, adapter, application, architecture, composition, contract, domain, other, quality |
| example | US-CA-03 R1.E1 | 2 | acceptance, domain |
| example | US-CA-03 R1.E2 | 3 | acceptance, adapter, domain |
| example | US-CA-03 R1.E3 | 2 | acceptance, domain |
| example | US-CA-03 R1.E4 | 3 | acceptance, domain |
| example | US-CA-03 R2.E1 | 2 | acceptance, domain |
| example | US-CA-03 R2.E2 | 2 | acceptance, domain |
| example | US-CA-03 R2.E3 | 1 | acceptance |
| example | US-CA-03 R3.E1 | 1 | acceptance |
| example | US-CA-03 R4.E1 | 4 | acceptance, adapter, domain |
| example | US-CA-03 R4.E3 | 4 | acceptance, adapter, domain |
| example | US-CA-03 S3.E1 | 2 | acceptance, domain |
| example | US-CA-03 S3.E2 | 2 | acceptance, domain |
| example | US-CA-03 S3.E3 | 2 | acceptance, domain |
| rule | INV-01 | 19 | acceptance, adapter, application, domain, other |
| rule | INV-02 | 11 | acceptance, adapter, application, domain |
| rule | INV-03 | 1 | acceptance |
| rule | INV-04 | 8 | acceptance, adapter, domain |
| rule | INV-36 | 4 | acceptance, adapter, application, domain |
| rule | INV-37 | 5 | acceptance, adapter, application, domain |
| rule | INV-38 | 6 | acceptance, adapter, application, domain, other |
| HTTP operation | createHelpRequest | 32 | adapter, contract |
| HTTP operation | provideHelp | 29 | adapter, contract, domain |
| HTTP operation | getHelpRequestById | 7 | adapter, contract |
| send operation | publishHelpRequestCreated | 5 | adapter |
| send operation | publishHelpProvided | 1 | adapter |
| receive operation | consumeGrandmaHelpProvided | 13 | adapter, contract |
| domain event | HelpRequested | 9 | adapter, application, domain |
| domain event | HelpProvided | 5 | adapter, application, domain |
| quality scenario | QS-02 | 1 | architecture |
| quality scenario | QS-14 | 2 | composition, quality |

US-CA-03: done for the increment's slices; planned: R4.E2 (inc 1), R4.E4 (inc 1), R5.E1 (inc 1), R5.E2 (inc 1), R6.E1 (inc 1), R7.E1 (inc 1), R7.E2 (inc 1), R8.E1 (inc 1), R8.E2 (inc 1), R8.E3 (inc 1), R8.E4 (inc 1)

Rules without a domain-level test: INV-03
planned http operations: listHelpRequests (inc 1), cancelHelpRequest (inc 1), listHelpsOfHelpRequest (inc 1), getHelpOfHelpRequest (inc 1), acceptHelp (inc 1), rejectHelp (inc 1), listHelps (inc 1)
planned send operations: publishHelpAccepted (inc 1), publishHelpRejected (inc 1), publishHelpRequestCancelled (inc 1), publishHelpRequestTimedOut (inc 1)
planned quality scenarios: QS-01 (inc 1), QS-03 (inc 1), QS-04 (inc 1), QS-05 (inc 1), QS-07 (inc 1), QS-08 (inc 1), QS-09 (inc 1), QS-10 (inc 1), QS-11 (inc 1), QS-12 (inc 1), QS-13 (inc 1), QS-15 (inc 1), QS-16 (inc 1)

No gaps.
