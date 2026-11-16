# OkeyBot

Android app (Kotlin) that watches the screen and plays your own Okey game with touch gestures, for a screen-recorded demo.

## Status

- [x] Milestone 1: game brain + unit tests (`brain` module, pure Kotlin/JVM)
- [ ] Milestone 2: screen capture + tile detection (needs screenshots of your game)
- [ ] Milestone 3: calibration screen
- [ ] Milestone 4: AccessibilityService gestures
- [ ] Milestone 5: full game loop
- [ ] Milestone 6: polish, overlay stop button, full README

## Run the tests (Milestone 1)

Requires JDK 17+ and Gradle. First time only, create the wrapper:

    gradle wrapper --gradle-version 8.7
    ./gradlew :brain:test

## Rules implemented

- 106 tiles: 4 colors x 1..13 x 2, plus 2 fake jokers.
- Okey = indicator color, number + 1 (13 wraps to 1). Real Okey tiles are wild.
  The fake joker is a normal tile that takes the Okey's color and number.
- Runs: 3+ consecutive, same color. 12-13-1 is optional (`RulesConfig.allowWrapRuns`).
- Groups: 3-4 tiles, same number, different colors.
- `Solver` finds the arrangement with the fewest unmatched tiles (memoized backtracking).
- `Strategy` takes the discard only if it strictly reduces unmatched tiles, and discards the tile that leaves the best hand (never the wild unless forced).
