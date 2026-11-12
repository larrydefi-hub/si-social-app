# SI SOCIAL app (Android and iOS)

The SI SOCIAL phone app: a Capacitor shell that opens **https://sisocial.fun** and adds the native parts
(push notifications, deep links, the back button, call audio). The website itself lives in a separate private
repository; nothing secret is in this one. Signing keys and the Firebase file are kept in this repository's
**Secrets**, which stay hidden even though the code is public.

## Build the Android app (from a phone)

1. **Actions → Mobile apps → Run workflow**, enter a version (for example `1.4.0`), run it.
2. In about 10 minutes the app is published as a release here, and **sisocial.fun/download** offers it.
   Members with the app see an "update the app" message.

### Secrets (Settings → Secrets and variables → Actions)

- `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`:
  the **same** upload key as before (from `SECRETS-README.txt`), so new versions install over the old app.
- `GOOGLE_SERVICES_JSON`: the whole text of the Firebase `google-services.json` (push notifications).

More detail: [mobile/README.md](mobile/README.md).
