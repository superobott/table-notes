# Review findings: verification and Galaxy S25+ checklist

The nine task commits were prepared on fix/review-findings from android-app at 2409c821eeb776eff91a4cff03f0ab92b2268ddf, then merged into main via PRs #2 and #1.
All nine findings were confirmed by reading the original branch before editing.
These changes are implemented, but local builds and tests did not start. The first PR CI run assembled the debug APK and passed all 11 JVM tests, then failed lint on redundant backup exclusions. Those exclusions are corrected. The final CI run passed debug assembly, lint and all 11 JVM tests (8 backup tests and 3 lookup tests); lint reported 0 errors and 19 warnings.
No phone behavior has been verified. No signing credentials were used.

The passing CI run covers the completed code for all nine tasks. The per-task local attempts below remained blocked by missing Java.

## Task status

| Task | Initial finding / status | Files changed | Verification performed |
| --- | --- | --- | --- |
| 1 Signing | Confirmed / implemented | app/build.gradle, .gitignore, README.md | Inspected optional config, all-four-value guard and ignored credential file; check attempt failed before Gradle. No signed-release or update test. |
| 2 Backup | Confirmed / implemented | AndroidManifest.xml, data_extraction_rules.xml, backup_rules.xml, MainActivity.java, policies/privacy.html | Reviewed database-only allowlists (sharedpref excluded) for both XML formats; timestamp is after stream close. Check attempt failed. Actual cloud/D2D restore needs phone. |
| 3 Discovery lifecycle | Confirmed / implemented | AndroidManifest.xml, DiscoverActivity.java | Traced session/revision cache, first empty-session load, explicit refresh/setup/area paths, configChanges, dialog cleanup and saved state with no key/listings. Check attempt failed; rotation needs phone. |
| 4 Places/Keystore | Confirmed / implemented | DiscoveryClient.java, DiscoverySettings.java, DiscoverActivity.java | Both deinitialize calls guarded; encryption errors wrapped separately from preference commit errors; invalidated key has explicit status. Check attempt and refinement recheck failed; real Keystore needs device. |
| 5 Draft discard | Confirmed / implemented | MainActivity.java | Reviewed Cancel/Back interception, outside-touch setting, changed-draft comparison and saved original baseline. Check attempt failed; gestures/rotation need phone. |
| 6 Duplicate reviews | Confirmed / implemented | MainActivity.java, PlaceIdLookup.java | Bound SQL lookup by exact ID; existing personal fields go to edit, oldest existing duplicate wins, no existing rows deleted. Check attempt failed; JVM tests added in task 9. |
| 7 Restore success | Confirmed / implemented | MainActivity.java | Success is after endTransaction returns; parse-before-confirm, replace-only transaction retained. Check attempt failed; fault injection/device test pending. |
| 8 Accessibility/layout | Confirmed / implemented; TalkBack needs device | MainActivity.java, DiscoverActivity.java | Reviewed wrap_content/minHeight, API-guarded stateDescription, separate accessible Maps action, numeric keyboard and comma parsing. Check attempt failed. |
| 9 Tests | Confirmed missing coverage / JVM passed in final CI; migration not run | app/build.gradle, MainActivity.java, JournalDatabase.java, PlaceIdLookupTest.java, JournalDatabaseMigrationTest.java, REVIEW-FINDINGS.md | Three lookup JVM tests passed in final CI (plus eight existing backup tests). Instrumented migration test uses production helper, separate DB, row preservation/reopen checks. No Robolectric; only AndroidX test dependencies added. Both commands failed before Gradle. |

Paths without a prefix are under app/src/main (manifest, res/xml or java/com/nikita/restaurantjournal); JVM tests are under app/src/test/java and the migration test is under app/src/androidTest/java.

## Actual local command output

On Windows, the requested command was run as:

```text
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

It was attempted after EACH task (1, 2, 3, 4, 5, 6, 7, 8, 9), and again after
the task 4 exception refinement and task 2 backup-rule correction. Every attempt returned exit code 1 with:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
```

The same actual error occurred for .\gradlew.bat connectedDebugAndroidTest.
No assemble, lint, JVM or instrumented tests passed locally. git diff --check
passed after the changes; it is a whitespace check, not compilation.
No Android SDK was found in the usual local Android directory.

Use JDK 17, Android SDK 35 and Build Tools 35.0.0 to rerun the required checks.
Run ./gradlew connectedDebugAndroidTest with a connected test device/emulator
for the migration test. The existing PR workflow runs build/lint/JVM checks;
it does not execute the instrumented test or verify a physical phone.

## PR CI evidence

Initial run: https://github.com/superobott/table-notes/actions/runs/38167180232

- The debug APK was produced; the artifact includes app-debug.apk.
- JUnit XML reports: PlaceIdLookupTest tests=3, failures=0, errors=0; BackupCodecTest tests=8, failures=0, errors=0.
- Overall command failed at lintDebug: 9 errors, 19 warnings. All 9 errors were FullBackupContent: redundant exclusions outside the database-only include lists. They were removed; an include list already excludes every other domain, including sharedpref. No lint suppression was added.
- Actual final log: BUILD FAILED in 2m 2s; 48 actionable tasks: 48 executed.
- Kotlin metadata compatibility diagnostics also appeared from the existing Places 5.2.0 dependency during lint analysis; dependency/toolchain versions are unchanged in this scoped task.
- The corrected code passed [final CI run 38214205017](https://github.com/superobott/table-notes/actions/runs/38214205017) on commit 4c8a98eb9c7e3c7587d2640006f2d36e5f4b88e0, subsequently merged into main. Actual output: BUILD SUCCESSFUL in 2m 17s; 48 actionable tasks: 48 executed. Final JUnit XML: 3 lookup tests and 8 backup tests, with 0 failures and 0 errors. Final lint XML: 0 errors, 19 warnings. Nonfatal Places/Kotlin metadata diagnostics remain. The instrumented migration test was not executed by this workflow.

## CodeRabbit review

On 12 October 2026, a CodeRabbit CLI review of committed main-branch changes against pre-Android commit b880e3d47f631dfa2f64a8ed924769f237c873cb completed: 32 files reviewed, 1 minor finding about stale verification documentation. The Gradle wrapper JAR was excluded as a binary. This documentation update addresses that finding using the actual final CI evidence. No follow-up CodeRabbit review of this documentation commit has been run. This is separate from the interrupted GitHub PR review and does not verify device behavior.

## Physical phone verification still required

- Discover results, active requests and all open dialogs across rotation; confirm no additional billed search on rotation/reopening with results.
- Android cloud backup restore and device transfer: journal retained, all preferences excluded, Google setup requires re-entry.
- TalkBack focus/activation of journal detail and nested Maps action, grade announcement, and large font/display scaling.
- Same-key update-over-install with existing journal data, and different-key update rejection.
- Real Android Keystore first save, invalidation and failure handling; Samsung keyboard and gesture Back behavior.

## Galaxy S25+ manual checklist (not executed)

Before installation or replacement tests, export existing journal data to a separate safe location and keep the original APK/signing identity. Use a spare device/profile or disposable dataset for restore failure and backup-transfer experiments; do not uninstall the user's only copy.

- [ ] Record Android/One UI version, APK commit, package and signing certificate. Install an update signed with the SAME original key over the existing app; verify names, IDs, grades, comments and timestamps remain. Try a different-key update only on a test installation and verify rejection.
- [ ] With no Google setup and airplane mode enabled, add/edit/search/sort/delete personal entries and export/restore a valid JSON file. Removing setup must not touch the journal.
- [ ] On a fresh test install, open setup and choose Remove, including repeated removal. Then save a valid restricted key and HTTPS policies before/after setting an area. Verify no SDK reset crash. Do not record the key in screenshots/logs.
- [ ] Using a disposable/restricted Google project, load results once. Count requests in the project metrics: rotate portrait/landscape and reopen Discover; these should reuse the session results. Update now should request again. Save setup/area changes should refresh the changed search. An empty returned list should remain displayed without repeat searches merely from reopening.
- [ ] Rotate with setup (including an unsaved key), coordinates, area chooser, signing identity and removal confirmation open. Verify dialog and typed contents remain during rotation. Close Discover and check for leaked-window/crash messages. After process death, provider listings/key drafts must not be restored from saved state; use Update now for a fresh search.
- [ ] Add a review from Discover, enter your own name/comment and save. Repeat Add my review for the same ID: existing grade/comment opens for editing; save and verify entry count stays unchanged. Distinct IDs and unlinked manual entries must remain separate. Existing duplicates from earlier versions must not be removed.
- [ ] Change each editor field individually; test Cancel and hardware/gesture Back, Keep editing and Discard. Outside taps must keep the draft. Rotate after editing and while confirmation is open; changed drafts must still ask. An unchanged draft should close directly. Test Back with the keyboard visible as well.
- [ ] Export a valid backup: Last backup changes only after successful completion and survives restart. Cancel/fail export: date unchanged. The line tracks manual JSON exports, not Android cloud backup; preference exclusion means it resets after transfer.
- [ ] Restore a valid v1 and v2 JSON file on a disposable dataset: confirmation states replacement, rows are replaced once, success appears after commit. Cancel or select malformed/oversized JSON: rows unchanged. Exercise a transaction failure on a test build/device and verify rollback/no success toast.
- [ ] Run connectedDebugAndroidTest to exercise v1-to-v2 migration, including preserved IDs, names, grades, comments and timestamps plus nullable/new place IDs. Also update a populated actual v1 test installation and inspect the preserved journal.
- [ ] On a spare device/profile, test Android backup/cloud restore and device transfer. Verify all journal rows remain and no encrypted API key/discovery preferences or Last backup timestamp transfer. Re-enter Google setup; the offline journal must already work.
- [ ] Enable TalkBack. Swipe through each journal card: detail/name and Maps should be separate actionable targets. Activate Maps, return, activate detail/Edit. Adjust the grade and verify announcement such as 8 of 10. Verify Discover buttons and attribution links are accessible.
- [ ] Use large font and display scaling in portrait/landscape; 52/56dp controls should grow without clipped labels. Enter coordinates with positive/negative values, decimal points and commas using Samsung keyboard; invalid/nonfinite/out-of-range values must be rejected.

## Design limits and references

Discovery listings are retained only in process memory (up to one session response),
never in SQLite, preferences, JSON exports or saved-state bundles. A process restart
loses these live listings. Place IDs remain the only persisted Google provider content.
The journal database migration remains the same additive ALTER TABLE as before.
The implementation added no new screen, map, localization or sharing and was not deployed. PRs #2 and #1 were later merged at the user’s explicit request.

Android backup formats: https://developer.android.com/identity/data/autobackup
Places initialization API: https://developers.google.com/maps/documentation/places/android-sdk/reference/com/google/android/libraries/places/api/Places
Instrumented runner: https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner
