# Table Notes 1.1 — verification status

- All Android/Google Java source compiled successfully with JDK 17 against Android API 35 and the official Google Places SDK 5.2.0 dependencies. Some platform APIs used for compatibility are deprecated.
- Six offline JUnit tests passed for backup compatibility, Unicode and Google-ID round trips, grade boundaries, all-record validation, version validation and timestamp overflow. These ran directly on the JVM with JUnit 4.13.2 and the real JSON library; they were not device or Google-service tests.
- The updated APK could **not** be produced here. Windows AAPT2 exited unexpectedly during resource linking, including after cache repairs, a clean rebuild and testing a different AAPT2 binary. Android lint and the full Gradle test/build pipeline have not completed for 1.1.
- The existing `TableNotes.apk` and `TableNotes-source.zip` supplied earlier are the **offline 1.0 version**, not the Google discovery update. They have not been replaced with an unverified build.
- `TableNotes-Google-source.zip` contains the 1.1 source for review/building, with `.coderabbit.yaml`, a GitHub Actions build/lint/unit-test workflow, and Google setup/public policy-page files. No API key or signing keystore is included.
- This source is being submitted to the private superobott/table-notes repository for GitHub Actions and CodeRabbit verification. Their results must be checked on the pull request; the local checks above do not establish CI or CodeRabbit success. Public policy pages have not been published.
- Live discovery and overnight scheduling remain untested because no Google Cloud project/API key or Android device is available. The database migration, actual app interactions, key storage and layout also need checks on a phone/emulator.

Next verification: put the source on a GitHub pull request in a repository authorized for CodeRabbit, run the Android workflow on Linux, inspect CodeRabbit's findings, and then test the resulting build on an Android phone after Google setup. A freshly built APK may use a different debug signing key from the existing installed app; export a backup before uninstalling or replacing it.
