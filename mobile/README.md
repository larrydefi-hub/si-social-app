# SI SOCIAL for Android and iOS

The apps are built with Capacitor. They load **https://sisocial.fun** inside a native app, so every website update
reaches the apps immediately, and add the native parts a website can't have:

- **Push notifications** through Firebase Cloud Messaging, using the same settings and the same "no duplicates" queue as browser push.
- **Deep links.** Tapping a sisocial.fun link opens the app (Android App Links and iOS Universal Links).
- **Android back button**, dark status bar, branded **splash screen** and **app icon**.
- **Offline screen**, plus an offline banner inside the app.
- **Wallet linking** with the Phantom or Solflare apps (view-only portfolio). The app never sees a seed phrase or private key.

## Build the Android app (no computer needed)

1. On GitHub, open the repository, then **Actions → Mobile apps → Run workflow**. Enter a version (for example `1.0.0`) and run it.
2. When the run finishes (about 10 minutes), download the file under **Artifacts**.
   - With no signing secrets, you get a test **.apk**. Install it on an Android phone to try the app.
   - With the signing secrets below, you get a **.aab** ready for Google Play.

### For Google Play (one time)

1. Create a Google Play developer account ($25 once).
2. Create an upload key. **From a phone:** GitHub → Actions → **Create Android upload key** → Run workflow, type `YES`,
   then download the artifact (deleted after 1 day). `SECRETS-README.txt` inside it lists the 4 secrets to add in
   step 3. Keep the downloaded files private and safe. **Or**, on any computer with Java installed, run:
   `keytool -genkey -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   Keep the file and its passwords somewhere safe. If you lose them, you can't update the app.
3. Add these GitHub secrets (**Settings → Secrets and variables → Actions**):
   - `ANDROID_KEYSTORE_BASE64`: the keystore file encoded as base64
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS` (for example `upload`)
   - `ANDROID_KEY_PASSWORD`
4. Push notifications:
   1. In the Firebase console, create a project and add an Android app with the package name `fun.sisocial.app`.
   2. Download `google-services.json` and paste its whole text into the GitHub secret `GOOGLE_SERVICES_JSON`.
   3. In **Project settings → Service accounts → Generate new private key**, download the JSON.
   4. In Render, add it as `FIREBASE_SERVICE_ACCOUNT`. This lets the server send pushes.
5. App Links:
   1. In Play Console, open **Setup → App signing** and copy the **SHA-256 certificate fingerprint**.
   2. Add it to Render as `ANDROID_CERT_SHA256`. Once both fingerprints are in, sisocial.fun links open the app.
   3. The workflow also prints your upload key's fingerprint. Add it too, separated by a comma.

## Build the iOS app

You need an Apple Developer account ($99 a year) and a Mac with Xcode, or a cloud Mac service.

1. The same workflow checks that the iOS build compiles and uploads the Xcode project as an artifact.
2. On a Mac:
   1. Unpack the project, run `npm install` in this folder, then open `ios/App/App.xcworkspace`.
   2. Select your team, then add the **Push Notifications** and **Associated Domains** capabilities. `App.entitlements` is already prepared.
   3. Archive, then upload to **TestFlight**.
3. Push on iOS:
   1. Upload an APNs key to Firebase (**Project settings → Cloud Messaging**).
   2. Add the iOS app to the Firebase project and put `GoogleService-Info.plist` in `ios/App/App`.
4. Universal Links: add your 10-character Apple Team ID to Render as `APPLE_TEAM_ID`.

## Store listing

- **App name:** SI SOCIAL
- **Short description:** Connect, create and earn: social feed, live Spaces, bounties and Solana tools.
- **Full description:** SI SOCIAL is one social home for creators, builders and Web3 communities. Post updates,
  photos, clips and stories, chat privately or in groups, and host or join live audio Spaces. Earn by completing
  funded bounties and gigs, paid in USDC on Solana. Follow live crypto prices, explore verified Solana tokens and
  see your own wallet's portfolio (view-only; no trading). SI SOCIAL never asks for your seed phrase or private keys.
- **Category:** Social
- **Privacy policy:** https://sisocial.fun/privacy
- **Support:** https://sisocial.fun/help
- **Content rating:** user-generated content, with in-app reporting, blocking and moderation.

**Google Play data safety answers.** These match what the platform actually does:

- Collected:
  - Name
  - Email
  - User ID
  - Photos and videos
  - Audio (Spaces)
  - Messages
  - Financial info (payment and transaction history)
  - Device IDs (push token)
- Not sold.
- Encrypted in transit.
- Users can ask for deletion.

**Financial features declaration.** SI SOCIAL holds users' USDC balances (deposits, earnings and withdrawals), and
Google treats that as a custodial service. Get legal advice for each country you publish in before you submit
(see the upgrade plan). You can limit the countries the app is offered in from Play Console.

## Screenshots

Take them on a phone (Home, Feed, a Space, Bounties, Explore Solana, Portfolio). Google Play needs at least 2.
The App Store needs 6.7-inch and 6.5-inch iPhone sizes.

## How it fits together

| File | What it does |
| --- | --- |
| `capacitor.config.json` | App id `fun.sisocial.app`; loads sisocial.fun; uses `www/offline.html` when there is no connection |
| `scripts/configure.mjs` | Adds the deep links, microphone and notification permissions, and wallet app queries to the generated projects |
| `resources/` | Icon (1024px), adaptive icon layers and splash screen, turned into every size by `npm run assets` |
| `../src/components/layout/native-bridge.tsx` | Website side: push sign-up, notification taps, deep links, back button and offline banner. Does nothing in a normal browser. |
| `../src/lib/services/fcm.ts` | Server side: sends pushes through Firebase |
