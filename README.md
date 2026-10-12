# Table Notes 1.1 for Android

A personal restaurant journal for Android 8 or newer, with whole-number grades from **0 to 10**, editable comments and optional **Google Maps restaurant discovery**. Designed for phones including the Galaxy S25+.

## Your journal works offline

- Add, edit and delete restaurants, grades and comments.
- Tap an entry to read its complete comment.
- Search names/comments and sort by recent edits, grade or name.
- Export a JSON backup or restore one after confirmation. Old version-1 backups still work. New backups include optional Google place IDs, but never API keys or provider listings.
- Your data stays in a private SQLite database. The app has no journal account or journal-upload service. Export backups regularly: uninstalling or clearing storage removes local data.

## Discover restaurants with Google Maps data

Tap **Discover restaurants** to search up to 20 restaurants around a chosen area, with a 1, 3, 5, 10 or 20 km radius. Google provides the live name, address and listed business status; your own grade remains separate. Open a Maps link or add your own journal entry linked to the Google place ID.

Discovery requires an internet connection and your own restricted Google Places API key, billing-enabled Google Cloud project, and public policy-page links. Follow [GOOGLE-SETUP.md](GOOGLE-SETUP.md). No key or account is provided with the app. Do not paste an API key into chat or commit it to GitHub.

Daily updates use Android JobScheduler for the **saved search area**. They do not track your phone in the background. Android can delay them; **Update now** is also available. Removing Google setup stops updates without touching journal entries. Opening the app checks that its daily job is still scheduled.

Google content is not retained as an offline restaurant directory. Only provider place IDs are stored; displayed names/addresses/status load online. A search is a selection, not a complete list of restaurants. “New to these results” does not mean a new restaurant has opened.

## Installation

Transfer the supplied `TableNotes.apk` to the phone and open it in My Files. Follow Android's installation prompts. This is a development APK for personal use. Install an update over the old app using the same signing identity to retain the journal; export a backup first.

## Build and checks

Open this folder in Android Studio with JDK 17, SDK Platform 35 and Build Tools 35.0.0. The Gradle wrapper uses 8.11.1 and Android Gradle Plugin 8.9.1. The official Places SDK 5.2.0 and Java-library desugaring are included.

Run `./gradlew assembleDebug lintDebug testDebugUnitTest` on Linux/macOS, or `gradlew.bat assembleDebug lintDebug testDebugUnitTest` on Windows. No Google API key is needed for compilation or the offline backup tests. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The included GitHub Actions workflow performs those checks on a pull request. It does not test live Google requests, a physical phone, or actual overnight scheduling. See [VALIDATION.md](VALIDATION.md) for the supplied build's actual verification status and [REVIEW.md](REVIEW.md) for CodeRabbit setup.

Key files: `MainActivity.java` implements the journal and database migration; `DiscoverActivity.java` implements discovery setup and results; `DiscoveryClient.java` makes official SDK calls; `DiscoverySettings.java` encrypts the key with Android Keystore; `DailyDiscoveryJob.java` schedules daily ID queries; `BackupCodec.java` handles versioned journal backups.

## Release signing and updates

Keep the same signing key for every update. An APK signed with a different key
cannot update an installed copy, including a debug APK signed with another
machine's debug key. Export the journal before changing installation/signing.
For an existing installation, retain its original signing key; creating a new
release key does not make it compatible with that installation.

Create a release key outside this repository using JDK keytool (enter passwords
at its prompts, rather than putting them in shell history):

```sh
keytool -genkeypair -v -keystore /safe/path/table-notes-release.jks -alias table-notes -keyalg RSA -keysize 3072 -validity 10000
```

Back up the keystore in at least two secure locations, with an encrypted offline
copy. Keep the alias and passwords in a password manager and verify that a backup
can be read with keytool. Losing the key prevents updates to existing installs.
Never commit a keystore, passwords, API keys or local.properties.

Set TABLE_NOTES_STORE_FILE (absolute keystore path), TABLE_NOTES_STORE_PASSWORD,
TABLE_NOTES_KEY_ALIAS and TABLE_NOTES_KEY_PASSWORD in the environment. Alternatively,
put the same four property names in root signing.properties, which is git-ignored.
Use forward slashes for Windows paths in that file. Environment values take
precedence. Run ./gradlew assembleRelease (gradlew.bat on Windows).
Without all four values, debug builds still work and release APKs remain unsigned;
there is no fallback to a different signing key.
