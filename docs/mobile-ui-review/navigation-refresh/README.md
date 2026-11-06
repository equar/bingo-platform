# Player UI and navigation refresh

The login screen keeps the Bingo Ethiopia artwork and BINGO balls. Home starts with the orange Play Bingo action, followed by the four existing activity cards and Play & Win panel.

The four primary destinations remain Home, Games, Wallet, and Profile. Each destination has its own navigation stack. Switching tabs preserves room details, wallet history tabs, and scroll state; selecting the current tab returns to its first screen. System back first closes a detail screen, then returns to Home from another tab. Navigation remains visible on room, community, and support pages.

UI refinements include consistent typography, card and form styling, more prominent room prices and room-detail actions, a navy wallet summary, clear support shortcuts, profile navigation shortcuts, and a less prominent logout action. Existing localization and text scaling remain supported.

The changes are in the Flutter source. No new mobile package was installed or published.

## Previews

These 390 × 844 logical-pixel renders use local test fixtures; room and wallet values are sample data.

- [Login](login.png)
- [Home](home.png)
- [Rooms](games.png)
- [Wallet](wallet.png)

## Validation

`flutter analyze --no-pub` reports no issues. `flutter test --no-pub` passes all 102 tests. Added navigation checks cover retained room routes, system back, retained wallet tabs, and Amharic layouts with 2× text on small phones. Existing responsive tests also cover tablets and landscape.
