# Module 04B-1 — Ticket Information and Read-Only Preview Test Matrix

Date: 2026-10-25. Model: server-generated random card at successful purchase. This matrix covers read-only preview; it does not authorize purchase, card selection, reservations, or posting.

| ID | Requirement | Expected behavior | Evidence / result |
| --- | --- | --- | --- |
| B1-01 | Establish ticket model | Purchase creates a new server-generated card; request contains no card ID or client price. | Service/entity/DTO inspection; documented in [design](module-04b1-ticket-selection-design.md). PASS |
| B1-02 | Decide reservations | No shared finite inventory exists, so reservation state is unnecessary under current model. | Design review. PASS; future specific-card inventory requires Product decision. |
| B1-03 | Navigate from room session | Session details expose Ticket information route. | Flutter widget test and Android E2E. PASS |
| B1-04 | Display authoritative price | Preview reads and displays session ticket price/currency from authenticated GET response. | Flutter widget test; focused MySQL endpoint assertion. PASS |
| B1-05 | Display card rules | Explain generated-at-purchase behavior, B/I/N/G/O ranges, and free center; state clearly this is not an assigned ticket. | Flutter widget test and Android screenshot. PASS |
| B1-06 | Display availability and eligibility | Show current session state and remaining seats from capacity minus ticket count; explain room/session/full status. | Flutter widget test; Android existing DRAFT session. PASS for available UI states; Android fixture is DRAFT. |
| B1-07 | Disable purchase | No purchase control can submit in QA preview; no successful purchase is simulated. | Widget and Android assertions verify `onPressed == null`. PASS |
| B1-08 | Loading and error/retry | Show loading and controlled retry on read failure. | Flutter widget tests. PASS |
| B1-09 | Refresh | Re-fetch session/room state without mutation. | Flutter widget test; Android refresh. PASS |
| B1-10 | Re-entry / restart expectation | Returning to preview reloads read-only state; no card selection is persisted. | Android navigation re-entry and a separate force-stop/relaunch journey both passed. The app showed login after relaunch in the driver context, then reloaded preview. PASS; no client selection state exists. |
| B1-11 | Authentication and account restriction | Player endpoints require PLAYER; suspended/unauthenticated account cannot use protected route or purchase. | Existing focused backend integration tests. PASS |
| B1-12 | Invalid identifiers | Invalid room/session identifiers return controlled not-found response. | Existing focused backend integration tests. PASS |
| B1-13 | Ineligible session / inactive room | Purchase rejects DRAFT/terminal states and inactive rooms without writes; preview explains observable session state. | Existing focused backend tests + Flutter DRAFT preview test. PASS |
| B1-14 | Price and client financial input | Client sends no price or wallet amount; server uses session snapshot. | Contract inspection and focused backend purchase-price tests. PASS |
| B1-15 | Read-only integrity | Preview GETs do not issue tickets, reservations, wallet transactions, or draws. | Android test + read-only persistent reconciliation. PASS |
| B1-16 | Flutter suite / analyzer | All player tests pass and no analyzer issues. | Full Flutter **29/29**, analyzer clean. PASS |
| B1-17 | Backend test suites | Focused and full backend suites pass on real MySQL `bingo_qa_test`. | Focused **18/18**; full **150/150**, zero failures/errors/skips. PASS |
| B1-18 | Real Android E2E | Login → lobby → room → ticket information → price/rules/disabled purchase → refresh → re-entry and relaunch. | **2/2 journeys passed** on emulator-5554. [Android results](../evidence/module-04b1-android-results.txt), [screenshot](../evidence/module-04b1-android-ticket-preview.png). |
| B1-19 | Persistent QA unchanged | Preserve existing rooms, sessions, player wallets, tickets, draws, claims, entitlements, wallet transactions, and agent float. | [Read-only SQL](../evidence/module-04b1-database-readonly.sql) and [output](../evidence/module-04b1-database-readonly.txt). |

Module 04B-1 is limited to the existing generated-at-purchase model. Existing Module 04B purchase accounting and schedule-boundary gaps are not closed by this read-only flow; Module 04B remains PARTIAL.
