# Table Notes 1.1 — verification status

- GitHub Actions run 37682892963 passed debug assembly, Android lint and six backup tests for commit 0adf14361d92a25c1c369a4643840a4aa8425fba.
- CodeRabbit reviewed PR #1 and identified five actionable findings. Follow-up changes stop pending location requests when removing setup, preserve restore confirmation across recreation, restrict discovery IDs to results from the non-exported activity, reject exports exceeding restore row/byte limits, and disable CI checkout credential persistence.
- Follow-up Java source compilation passed locally. Eight standalone backup tests passed, including row and UTF-8 byte export limits. The follow-up commit still needs its own GitHub Actions result and CodeRabbit re-review.
- The locally supplied TableNotes.apk remains offline version 1.0. No updated APK has been downloaded or delivered yet.
- Live Google discovery, overnight scheduling, migration, restore during rotation and the phone UI require Android device testing. No API key or signing keystore is committed. Public policy pages remain unpublished.

Next: verify the follow-up CI/review, prepare an installable APK, test on the Galaxy S25+, then configure Google discovery. Export existing journal data before installing a build signed with a different certificate.
