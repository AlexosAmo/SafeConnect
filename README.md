# SafeConnect

SafeConnect is an Android-first community safety app for South Africa, built in Kotlin. It lets people report incidents to their community, share live journeys with trusted contacts, trigger an SOS, and check their own safety readiness, and it keeps working when the network does not.

> **Demo video:** [Watch the full feature walkthrough (YouTube, unlisted)](PASTE_YOUTUBE_LINK_HERE)
> **Latest release / signed APK:** [v2.0.0 on GitHub Releases](https://github.com/AlexosAmo/SafeConnect/releases)
> **Release notes:** [RELEASE_NOTES.md](RELEASE_NOTES.md)

## Screenshots

| Login / biometric | Community feed | Safety assessment |
|---|---|---|
| ![Login](screenshots/login.png) | ![Feed](screenshots/feed.png) | ![Assessment](screenshots/assessment.png) |

| Journey tracking | SOS | Dark theme |
|---|---|---|
| ![Journey](screenshots/journey.png) | ![SOS](screenshots/sos.png) | ![Dark](screenshots/dark.png) |

*(Replace with your own screenshots in the `/screenshots` folder.)*

## Features

- **Authentication:** Firebase Authentication with Google sign-in and optional biometric unlock.
- **Profile:** editable name, email, phone, province and photo, plus a SafeConnect ID derived from the Firebase user.
- **Community feed:** province-aware, live incident reports via Firestore snapshot listeners. Public reports contain no precise coordinates and no reporter UID.
- **Offline-first incident reporting:** reports are saved in Room first, then delivered by WorkManager when a connection returns. The UI distinguishes Queued, Sent and Confirmed.
- **Journey tracking and sharing:** live location sharing with linked contacts, with a configurable check-in interval.
- **SOS:** in-app SOS with opt-in direct SMS and sent/delivered status callbacks.
- **Safety readiness assessment:** eight self-reported questions, private history and a latest-result display.
- **Safety AI guidance:** 15-second timeout with a local fallback so users always get guidance.
- **Settings:** nearby-alert radius, automatic journey sharing, check-in interval, feed category filters, discreet SOS preference.
- **Languages:** English, Afrikaans, isiZulu, Sesotho, with starter resources for Sepedi, Setswana and isiXhosa.
- **Themes:** light and dark.

## Architecture

| Layer | Technology |
|---|---|
| UI | Kotlin, AndroidX, XML layouts |
| Networking | Retrofit |
| Local storage | Room (database migration 4 to 5) |
| Background work | WorkManager |
| Identity | Firebase Authentication |
| Cloud data | Cloud Firestore, Firebase Storage, Firebase Cloud Messaging |
| Backend | Spring Boot REST API (Azure App Service) verifying Firebase ID tokens |

Package layout (`com.amogelang.safeconnect.app`): `firebase`, `model`, `network`, `offline`, `repository`, `safety`, `settings`, `util`, plus the Activity classes at the package root.

## Logging

The app uses a small wrapper, `util/AppLog.kt`, around `android.util.Log`. Logs are tagged per component (`SC/Offline`, `SC/Auth`, `SC/Sos` and so on) so the flow of a feature can be followed in Logcat. Tokens, report text, personal details and precise locations are never logged. See [CODE_GUIDE.md](CODE_GUIDE.md).

Useful Logcat filter: `tag:SC/`

## Build and run

1. Open the project root (the folder containing `settings.gradle.kts`) in Android Studio.
2. Place your own `app/google-services.json` in `app/`.
3. Sync Gradle, then run on an emulator or device.
4. Optional API base URL: `-PsafeConnectApiBaseUrl=http://10.0.2.2:8080/` (local) or your Azure URL.

```
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Google sign-in setup

Enable Google under Firebase Authentication, register your debug and release SHA-1/SHA-256 fingerprints, then replace `google-services.json` with the refreshed file.

## Known limitations

- Direct SMS needs the `SEND_SMS` permission, which Google Play restricts to eligible use cases. A server-side SMS provider is the recommended route for a public release.
- Some translations are starter resources and need professional review.
- No offline device can guarantee that an emergency message reached another person, so the UI shows honest delivery states.

## Security

No service-account keys or keystores are committed. Firestore and Storage rules are restrictive. Production use would need a privacy policy, consent flows and a Play Data Safety declaration.

## References and AI declaration

Third-party sources are listed in [REFERENCES.md](REFERENCES.md). The AI tools write-up is submitted separately.
