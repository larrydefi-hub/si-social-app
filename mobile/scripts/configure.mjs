// After `npx cap add android|ios`: adds what the generated projects don't have by default.
//   Android: App Links for sisocial.fun, camera (video posts and Reels), mic permission (Spaces), notification permission (Android 13+), an
//            "ongoing call" service so calls keep their sound and microphone in the background,
//            "queries" so links can open the Phantom / Solflare apps for wallet approvals, and screen streaming
//            for Mobile Gaming Live (MediaProjection + RTMP to the member's stream key).
//   iOS:     Universal Links (associated domains), mic usage text, background audio for Spaces.
import fs from 'node:fs';

const platform = process.argv[2];
const insertOnce = (file, marker, text, where = 'before') => {
  let s = fs.readFileSync(file, 'utf8');
  if (s.includes(text.trim().split('\n')[0].trim())) return;
  if (!s.includes(marker)) throw new Error(`${file}: can't find ${marker}`);
  s = s.replace(marker, where === 'before' ? `${text}\n${marker}` : `${marker}\n${text}`);
  fs.writeFileSync(file, s);
};

if (platform === 'android') {
  const manifest = 'android/app/src/main/AndroidManifest.xml';
  // Camera: record videos for posts and Reels in the app.
  insertOnce(manifest, '<application', `    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />`);
  insertOnce(manifest, '<application', `    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <queries>
        <package android:name="app.phantom" />
        <package android:name="com.solflare.mobile" />
        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="https" /></intent>
    </queries>`);
  insertOnce(manifest, '</activity>', `            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="sisocial.fun" />
                <data android:scheme="https" android:host="www.sisocial.fun" />
            </intent-filter>`);
  // Calls keep going in the background: an "ongoing call" foreground service (microphone + sound).
  insertOnce(manifest, '<application', `    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />`);
  insertOnce(manifest, '</application>', `        <service android:name=".CallForegroundService" android:exported="false" android:foregroundServiceType="microphone|mediaPlayback" />`);
  // Mobile Gaming Live: stream the phone's screen (MediaProjection) to the member's stream key over RTMP.
  insertOnce(manifest, '<application', `    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`);
  insertOnce(manifest, '</application>', `        <service android:name=".ScreenStreamService" android:exported="false" android:foregroundServiceType="mediaProjection|microphone" />`);
  // The RTMP encoder (RootEncoder, Apache 2.0) comes from JitPack.
  const settings = fs.existsSync('android/settings.gradle') ? fs.readFileSync('android/settings.gradle', 'utf8') : '';
  if (/dependencyResolutionManagement[\s\S]*repositories\s*\{/.test(settings)) {
    if (!settings.includes('jitpack.io')) fs.writeFileSync('android/settings.gradle', settings.replace(/(dependencyResolutionManagement[\s\S]*?repositories\s*\{)/, "$1\n        maven { url 'https://jitpack.io' }"));
  } else {
    let root = fs.readFileSync('android/build.gradle', 'utf8');
    if (!root.includes('jitpack.io')) {
      if (!/allprojects\s*\{\s*repositories\s*\{/.test(root)) throw new Error('android/build.gradle: no allprojects.repositories block');
      root = root.replace(/(allprojects\s*\{\s*repositories\s*\{)/, "$1\n        maven { url 'https://jitpack.io' }");
      fs.writeFileSync('android/build.gradle', root);
    }
  }
  const appGradle = 'android/app/build.gradle';
  let ag = fs.readFileSync(appGradle, 'utf8');
  if (!ag.includes('RootEncoder')) {
    ag = ag.replace(/dependencies\s*\{/, (m) => `${m}\n    implementation "com.github.pedroSG94.RootEncoder:library:2.6.0"`);
    fs.writeFileSync(appGradle, ag);
  }
  const javaDir = 'android/app/src/main/java/fun/sisocial/app';
  for (const f of ['CallForegroundService.java', 'CallServicePlugin.java', 'ScreenStreamService.java', 'ScreenStreamPlugin.java', 'ChatOverlay.java']) fs.copyFileSync(`native/android/${f}`, `${javaDir}/${f}`);
  const main = `${javaDir}/MainActivity.java`;
  if (!fs.readFileSync(main, 'utf8').includes('CallServicePlugin')) {
    fs.writeFileSync(main, `package fun.sisocial.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CallServicePlugin.class);
        registerPlugin(ScreenStreamPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
`);
  }
  // Dark system bars before the page paints.
  const styles = 'android/app/src/main/res/values/styles.xml';
  if (fs.existsSync(styles)) {
    let s = fs.readFileSync(styles, 'utf8');
    if (!s.includes('android:statusBarColor')) s = s.replace(/(<style name="AppTheme.NoActionBar"[^>]*>)/, '$1\n        <item name="android:statusBarColor">#070710</item>\n        <item name="android:navigationBarColor">#070710</item>');
    fs.writeFileSync(styles, s);
  }
  console.log('Android project configured.');
} else if (platform === 'ios') {
  const plist = 'ios/App/App/Info.plist';
  insertOnce(plist, '</dict>\n</plist>', `\t<key>NSMicrophoneUsageDescription</key>\n\t<string>SI SOCIAL uses the microphone when you speak in a live Space or record a video.</string>\n\t<key>NSCameraUsageDescription</key>\n\t<string>SI SOCIAL uses the camera when you record a video for a post or a Reel.</string>\n\t<key>UIBackgroundModes</key>\n\t<array>\n\t\t<string>audio</string>\n\t\t<string>remote-notification</string>\n\t</array>\n\t<key>LSApplicationQueriesSchemes</key>\n\t<array>\n\t\t<string>phantom</string>\n\t\t<string>solflare</string>\n\t</array>`);
  const ent = 'ios/App/App/App.entitlements';
  if (!fs.existsSync(ent)) {
    fs.writeFileSync(ent, `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
\t<key>aps-environment</key>
\t<string>production</string>
\t<key>com.apple.developer.associated-domains</key>
\t<array>
\t\t<string>applinks:sisocial.fun</string>
\t\t<string>applinks:www.sisocial.fun</string>
\t</array>
</dict>
</plist>
`);
  }
  console.log('iOS project configured. In Xcode: add App.entitlements to the target and turn on Push Notifications + Associated Domains.');
} else {
  console.error('Usage: node scripts/configure.mjs android|ios');
  process.exit(1);
}
