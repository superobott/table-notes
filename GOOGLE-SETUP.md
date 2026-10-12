# Enable Google Maps restaurant discovery

Table Notes 1.1 uses the official Google Places SDK for Android, not scraping. No API key is supplied or embedded in the APK. You do not need to give a key to Codex, CodeRabbit or GitHub.

## One-time setup

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project for Table Notes. Link a billing account. Google determines the applicable API charges; the app cannot create or pay for this account on your behalf.
2. Enable **Places API (New)** in that project. Follow [Google's Android cloud setup guide](https://developers.google.com/maps/documentation/places/android-sdk/cloud-setup).
3. Create an API key. Under application restrictions choose **Android apps**. Add package `com.nikita.restaurantjournal` and the APK's SHA-1 signing-certificate fingerprint. On the phone, **Discover → Setup & updates → Copy app identity for key restrictions** shows both values. The SHA-1 is a public signing identifier, not a secret.
4. Restrict the key's API access to the service needed for the Places SDK's new API, following [Google's API-key guide](https://developers.google.com/maps/documentation/places/android-sdk/get-api-key). Set suitable request quotas in Google Cloud before enabling daily updates.
5. Google requires publicly accessible privacy and terms pages for apps using Places. Publish the supplied `policies/privacy.html` and `policies/terms.html` pages on a static host you control, review their contents, and retain their relative link. They are provided as files; they have not been published. Use the resulting public HTTPS URLs in the app. See [Google's policies](https://developers.google.com/maps/documentation/places/android-sdk/policies).
6. Install the supplied updated APK over the previous version. Export a journal backup first. Do not uninstall the old copy unless you have a backup.
7. Open **Discover → Setup & updates**, enter the restricted key privately on your phone and the published policy URLs, choose the radius and daily-update setting, then save.
8. Choose an area using your approximate location or coordinates. The saved area stays fixed until you change it. Tap **Update now** to test live discovery.

## What daily updates do

Android JobScheduler attempts a search roughly every 24 hours when internet is available. The job asks only for place IDs and stores IDs and timestamps, which are permitted to be retained. Live names, addresses, distances and business status are loaded for display when you open Discover. Both background and foreground requests may be billed; requesting fewer fields does not guarantee a free search.

The app returns up to 20 nearby restaurants in the selected radius, ranked by distance. Results are a selection, not every existing restaurant. A place missing from one search is not evidence it closed. Daily updates do not upload your grades/comments or back up your journal. Android may delay jobs or stop them after force-stop; reopening the app checks that a daily job is scheduled.

Google-provided names and addresses are not copied into permanent journal records. **Add my review** opens a blank entry where you enter your own restaurant name, grade and comment; the Google place ID is retained to provide a Maps link. This keeps your journal separate from Google's data and its storage restrictions.

## If discovery does not work

- Check the Places API (New), billing, API quota and key restrictions in the same project.
- Match the installed app's package **and actual signing certificate**. An APK rebuilt by Android Studio or GitHub Actions may have a different certificate.
- Check internet access and allow approximate location if using the location button; coordinates work without location permission.
- Your offline journal remains usable while setup is incomplete or Google's request fails.

Live Google requests and actual daily scheduling cannot be tested without your configured project and an Android device. The included offline tests check journal backup compatibility and validation, not Google's service.
