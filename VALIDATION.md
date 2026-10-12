# Table Notes 1.1 — verification status

Updated 12 October 2026.

- [GitHub Actions run 38214205017](https://github.com/superobott/table-notes/actions/runs/38214205017) passed assembleDebug, lintDebug and testDebugUnitTest for commit 4c8a98eb9c7e3c7587d2640006f2d36e5f4b88e0, subsequently merged into main via PRs #2 and #1. Actual output: BUILD SUCCESSFUL in 2m 17s; 48 actionable tasks: 48 executed.
- All 11 JVM tests passed: 8 BackupCodec tests and 3 PlaceIdLookup tests, with 0 failures or errors. Lint reported 0 errors and 19 warnings. Nonfatal Kotlin metadata diagnostics from the existing Places dependency appeared during lint analysis.
- The initial review-fix CI run failed lint on redundant backup exclusions. The database-only allowlists were corrected; the final run above passed. See [REVIEW-FINDINGS.md](REVIEW-FINDINGS.md) for the task table, original local command output and Galaxy S25+ checklist.
- CodeRabbit’s CLI review of committed main-branch changes completed on 12 October 2026: 32 files reviewed, one minor finding about stale verification documentation. This documentation update addresses that finding. The binary Gradle wrapper JAR was excluded. No follow-up CodeRabbit review of this documentation commit has been run.
- Local Gradle checks were attempted after each task but could not start because Java was unavailable. The instrumented v1-to-v2 database migration test was added but has not been run. CI produced a debug APK; this is not a signed-release or phone-validation result.
- Rotation, Android cloud backup/device transfer restore, TalkBack, signing continuity/update-over-install, live Google discovery, daily scheduling and the Galaxy S25+ UI still require device testing. No phone behavior is claimed from a passing build. No API keys, keystores or local.properties were committed.

Next: run the instrumented migration test on an emulator/device and complete the manual checklist on the Galaxy S25+. Export the current journal before installation tests and retain its original signing key; a differently signed APK cannot update the installed app.
