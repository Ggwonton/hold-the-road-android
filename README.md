# Rotmarch: Hold the Road — Android

A thin native wrapper around the game: a full-screen, landscape WebView that loads
`app/src/main/assets/index.html`. Progress saves to the WebView's local storage.

## Build

1. Open this folder in Android Studio (Koala or newer) and let it sync Gradle.
   If it asks for a Gradle wrapper, accept the default it offers.
2. Plug in a phone with USB debugging on, or start an emulator, and press Run.
3. For a store build: Build > Generate Signed App Bundle / APK.

## What the wrapper does

- Locks to landscape, hides the status and navigation bars, keeps the screen on.
- Back button: closes panels, then pauses or unpauses a battle; on the world map
  it sends the app to the background.
- Pauses the battle and silences audio when the app goes to the background.
- Vibrates when lives are lost and when a boss appears (turns off with Screen shake).

## Fonts offline

The game loads its fonts (Lilita One, Grenze Gotisch, Alegreya Sans) from Google Fonts
when the phone is online. To have them offline too, download the families from
fonts.google.com and drop these files into `app/src/main/assets/fonts/`:

    LilitaOne-Regular.ttf
    GrenzeGotisch-Bold.ttf
    GrenzeGotisch-ExtraBold.ttf
    AlegreyaSans-Regular.ttf
    AlegreyaSans-Bold.ttf
    AlegreyaSans-ExtraBold.ttf

## Updating the game

Replace `app/src/main/assets/index.html` with the new build and rebuild the app.

## Automatic builds on GitHub

`.github/workflows/build.yml` builds the game on every push to `main` (or from the Actions
tab with "Run workflow"). Each run produces two downloads:

- **HoldTheRoad-debug-apk**: install straight onto a phone for testing.
- **HoldTheRoad-Play-Bundle**: the `.aab` for the Play Console. It is signed only if you add
  the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
  (the same kind of upload key Rotmarch uses; a separate key per app is recommended).

To set it up: create an empty repository (for example `hold-the-road-android`), upload the
contents of this folder to it, and the first build starts by itself.

## Getting onto the Play Console test track

1. In the Play Console, create a new app (name, default language, game, free).
2. Fill in the required App content items (privacy policy, ads: none, content rating, target audience).
3. Testing > Internal or Closed testing > Create new release > upload the signed `.aab`.
4. Add testers (the same list as Rotmarch works) and send for review.

Package name: `com.ashenlantern.holdtheroad`. Change it in `app/build.gradle` before the first upload
if you want a different one; it cannot be changed afterwards.
