# Academic implementation evidence

Figma file: `KL6cHSklHrmrbuSnOnZgGu`.

Retrieved high-fidelity top-level design contexts and screenshots for all four owned screens:
- Academic `9:49442`
- Course detail `9:49832`
- Grade tracker `9:50965`
- Learning recommendations `9:51290`

These contexts include headings, spacing, typography, chart geometry, button/chip labels, component mapping and hierarchy, but Code Connect masks nested content slots. Slot-level context retrieval failed (plain slot IDs are not selectable nodes), then the Starter plan MCP quota was reached. No more Figma calls were made after quota diagnosis. The local screenshots were OCR-read with RapidOCR to recover visible task/course/grade/learning text. This is a fallback, not a claim of fully verified exact slot typography or punctuation. The vision provider rejects image inputs; pixel-perfect visual comparison was not available.

Screenshot evidence (Hermes cache):
- academic `img_11327793cec3.png`
- course `img_28f17f4494ae.png`
- tracker `img_66a62c5f905f.png`
- learning `img_f733b7e84152.png`

Outstanding nested slots for exact future verification:
- Academic: `9:49521`, `9:49551`, `9:49585`, `9:49632`, `9:49679`, `9:49726`
- Course: `9:49896`, `9:49977`, `9:50011`, `9:50046`, `9:50074`
- Tracker: `9:51036`, `9:51100`, `9:51129`, `9:51158`, `9:51187`
- Learning: `9:51361`, `9:51439`, `9:51466`, `9:51496`, `9:51524`

Resolve actual instance composite IDs from parent metadata before requesting these again; these plain slot IDs are only references.

All supplied SVG URLs in the four top-level contexts were fake system signal/battery assets. Native system bars replace these as instructed. The body uses connected Material3 components/icons, so no body SVG asset was substituted or downloaded.

Local behavior is backed by SharedPreferences `academic`: task completion, added tasks, letter/score grades, goal and learning plan. Counts and IPS recompute. Only visible Figma records were seeded: IF510's active task and six older IF570 completed task descriptions are not available and are explicitly reported as unavailable rather than invented. Reminder text is accompanied by a note that no OS notification is scheduled. Grade points are explicitly selected by the user, not inferred from unprovided university score thresholds. IPK and history are design fixture data, not remote university records.

Public composables in `org.umn.application.studenthub.ui.academic`:
- `AcademicScreen(onCourseClick: (String) -> Unit)`
- `CourseDetailScreen(courseId: String, onNotesClick: () -> Unit)`
- `GradesScreen()`
- `LearningScreen()`

Host owns Scaffold; each body scrolls with 24dp horizontal, 16dp top, 24dp bottom padding. No MainActivity, navigation, theme, build files or other feature files were edited by this worker.

Validation: focused `*academic.*` tests pass: 4 Robolectric Compose tests + 3 state tests, zero failures/errors. Full suite run had 27 tests and one unrelated `StudentHubUiTest.addedAgendaIsSavedForSelectedDay` AppNotIdleException. Gradle calls must hold `/home/sullivan/.hermes/cache/scratch/studenthub-gradle.lock` to avoid concurrent test result corruption.
