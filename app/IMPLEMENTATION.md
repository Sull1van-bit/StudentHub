# StudentHub Android implementation

Native Kotlin / Jetpack Compose implementation based on the supplied Figma file KL6cHSklHrmrbuSnOnZgGu, Page 1.

## Implemented screens

- Hari ini (9:49017): welcome, next class, agenda, reminders, shortcuts.
- Jadwal (9:49228): week/day selection, class links, validated local agenda creation.
- Akademik (9:49442): course list, task filters, completion and progress.
- Detail mata kuliah (9:49832): course tasks, local additions, progress, notes/grades tabs.
- Catatan (9:50176): search, course filters, create/open notes.
- Editor catatan (9:50479): editable title/body, explicit save, confirmed deletion.
- Keuangan (9:50669): monthly local transactions, income/expense balance, editable budgets.
- Pelacak akademik (9:50965): local grades, IPS, historical demo chart, target.
- Rekomendasi belajar (9:51290): learning checklist, target and plan editing.

All nine destinations are connected through StudentHubApp. MainActivity now starts that app. Earlier dashboard/repository files remain intact to preserve the existing worktree.

## Data and intentional limits

This is an offline implementation, not a campus/backend integration. Initial academic content and the displayed October 2026 date are Figma demo data. Notes, agenda additions, grades, tasks, budgets and transactions persist on-device with SharedPreferences. Checkboxes do not submit coursework. Reminder text does not schedule Android notifications. The editor's formatting buttons insert text markers rather than providing a full rich-text editor.

The Figma Starter MCP quota ran out after the nine top-level screen contexts were retrieved. Some nested component slots could not be inspected; academic and home text was recovered from the supplied screenshots with OCR. Hidden notes and financial transactions were not invented. Consequently the notes count and local financial balance differ from the design; known demo budget totals are explicitly distinguished from user-entered transactions. Exact pixel parity and complete nested-slot fidelity are NOT verified.

Original supplied non-system SVGs are stored in src/main/assets/figma, with rasterized xxxhdpi resources for Compose. Native Android renders the system status/navigation bars instead of simulated Figma system bars.

## Verification

Run from the project root:

    ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=2

Robolectric tests run at API 35. StudentHubUiTest exercises navigation across every screen, agenda persistence/recreation, and captures nine native-rendered screenshots in app/build/outputs/screenshots. Domain/UI tests cover notes, finance, academic state and learning interactions. Screenshot rendering plus OCR confirms non-empty screen content; this is not a pixel-comparison test.

Instrumented test APK can be built with :app:assembleDebugAndroidTest. The installed API 37 emulator repeatedly exited with SIGSEGV before adb became available, so real emulator/device execution was not completed. Use a working device to run :app:connectedDebugAndroidTest.

Debug APK: app/build/outputs/apk/debug/app-debug.apk
Lint report: app/build/reports/lint-results-debug.html
Test report: app/build/reports/tests/testDebugUnitTest/index.html
