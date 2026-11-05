# SafeConnect

SafeConnect is an Android-first community safety app for South Africa, built in Kotlin. It lets people report incidents to their community, share live journeys with trusted contacts, trigger an SOS, and check their own safety readiness, and it keeps working when the network does not.

> **Demo video:** [Watch the full feature walkthrough (YouTube, unlisted)](PASTE_YOUTUBE_LINK_HERE)
> **Latest release / signed APK:** [v2.0.0 on GitHub Releases](https://github.com/AlexosAmo/SafeConnect/releases)
> **Release notes:**
# Release Notes

## SafeConnect v2.0.0 (final submission)

Changes since the prototype.

### Innovative features added in this version

| Feature | What it does | Where in the code |
|---|---|---|
| **Offline-first incident outbox** | Reports are saved in Room first and delivered by WorkManager when a network returns. The UI shows Queued, Sent or Confirmed and never claims delivery early. | `offline/` |
| **Biometric unlock** | Fingerprint / face unlock protects the session. | `BiometricSessionManager`, `BiometricUnlockActivity` |
| **Safety readiness assessment** | Eight self-reported questions with private history and a latest-result display. | `SafetyAssessmentActivity`, `safety/` |
| **Safety AI guidance with fallback** | AI request has a 15-second timeout and falls back to local guidance, so the user always gets an answer. | `safety/`, `network/` |
| **Privacy-preserving community feed** | Province-aware live reports through Firestore snapshot listeners. Public reports contain no precise coordinates or reporter UID. | `CommunityFeedActivity`, `repository/` |
| **Live journey sharing** | Journey completion keeps location sharing running until the user separately stops it. | `TrackJourneyActivity`, `SharedJourneysActivity` |
| **Opt-in SOS SMS** | Direct SOS SMS with sent and delivery status callbacks. | `SosActiveActivity.kt` |
| **Multi-language support** | English, Afrikaans, isiZulu, Sesotho, plus starter Sepedi, Setswana and isiXhosa. | `res/values-*` |
| **Five user-configurable safety options** | Alert radius, auto journey sharing, check-in interval, feed filters, discreet SOS preference. | `settings/` |

### Other updates

- Editable profile (name, email, phone, province, photo) with a SafeConnect ID.
- Light and dark themes.
- Room database migration 4 to 5 (province fields).
- Google ID-token sign-in validation; Facebook SDK/login removed.
- Spring Boot REST API verifying Firebase ID tokens and writing incidents to Firestore.
- Structured logging through `util/AppLog.kt` (tagged, release-safe, no personal data).
- Code comments and source references added across the project.
- Unit tests and a GitHub Actions workflow.

### Fixes and improvements

- Add your own here (bugs found while testing, UI changes, performance fixes).

### Known limitations

- The discreet SOS activation gesture is a stored preference only.
- Direct SMS needs `SEND_SMS`, which Google Play restricts; a server-side provider is recommended for public release.
- Some translations still need professional review.

### Publication preparation

- Signed release APK attached to the GitHub Release.
- Screenshots in `/screenshots`.
- Google Play Console evidence: add your screenshot if you uploaded to an internal testing track, otherwise state why it is not applicable.

---

## Prototype (baseline)

SafeConnectis a personal and community safety app for the South African market, Many South Africans rely on phoning emergency services or scattered WhatsApp groups when something goes wrong. That is slow, and it often fails when mobile data is patchy or when reaching for a phone openly could make the situation more dangerous.
SafeConnect is a faster, more reassuring alternative that works without a paid subscription for basic protection


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

The app uses a small wrapper, `util/AppLog.kt`, around `android.util.Log`. Logs are tagged per component (`SC/Offline`, `SC/Auth`, `SC/Sos` and so on) so the flow of a feature can be followed in Logcat. Tokens, report text, personal details and precise locations are never logged.


## 1. Comments

Put a KDoc block on every class and every non-trivial function, and short `//` comments on the "why" of tricky lines.

```kotlin
/**
 * Uploads queued incident reports to the Spring Boot API.
 *
 * Runs through WorkManager so it only executes when a network is available.
 * A report is marked SYNCED only after the API confirms it, which keeps
 * retries safe (the incident ID is stable and acts as an idempotency key).
 *
 * Reference: Android Developers (2024) Schedule tasks with WorkManager.
 */
class OutboxSyncWorker(...) : CoroutineWorker(...) {

    override suspend fun doWork(): Result {
        // Fresh token on every attempt: ID tokens expire after about an hour.
        val token = ...
    }
}
```

Rules of thumb:
- Explain intent, not syntax. Avoid `// increment i`.
- Every borrowed idea, snippet or tutorial gets a reference comment next to it (see section 3).
- Comment on safety decisions: why precise coordinates are excluded, why a timeout exists.

## 2. Logging

Use `AppLog` (`util/AppLog.kt`). One component tag per class, and log decisions, state changes and failures rather than every line.

```kotlin
private const val COMPONENT = "Offline"

AppLog.d(COMPONENT, "Worker started")
AppLog.i(COMPONENT, "Incident ${AppLog.mask(incidentId)} marked SYNCED")
AppLog.w(COMPONENT, "No network, retry scheduled")
AppLog.e(COMPONENT, "API call failed", exception)
```

Suggested places:

| Area | What to log |
|---|---|
| Splash / Main | App start, signed-in or signed-out routing decision |
| Auth / Biometric | Sign-in success or failure, biometric result (never tokens) |
| Offline outbox | Saved to Room, worker start, retry, SYNCED |
| Community feed | Listener attached, detached, snapshot received with item count |
| Journey tracking | Start, stop, check-in due, location service state (never coordinates) |
| SOS | Triggered, SMS sent, SMS delivered or failed |
| Assessment | Started, completed, score calculated |
| AI guidance | Request sent, timeout, fallback used |
| Settings | Preference changed (key only) |

Never log: Firebase ID tokens, report text, phone numbers, emails, or exact latitude/longitude.

##App Logs
package com.amogelang.safeconnect.app.util

import android.util.Log
import com.amogelang.safeconnect.app.BuildConfig

/**
 * Small wrapper around [android.util.Log] used across SafeConnect.
 *
 * Why a wrapper?
 *  - Every message gets a consistent "SC/<Component>" tag, so the whole app can be
 *    followed in Logcat with the filter `tag:SC/`.
 *  - Verbose and debug output is switched off in release builds ([BuildConfig.DEBUG]).
 *  - It gives one place to enforce the privacy rule in our README: tokens, report
 *    text, personal details and precise locations must NEVER be logged.
 *
 * Reference: Android Developers (2024) Log. Available at:
 * https://developer.android.com/reference/android/util/Log
 */
object AppLog {

    private const val PREFIX = "SC/"

    /** Flow tracing, e.g. "Outbox worker started". Debug builds only. */
    fun d(component: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(PREFIX + component, message)
    }

    /** Important lifecycle or state changes, e.g. "Incident marked SYNCED". */
    fun i(component: String, message: String) {
        Log.i(PREFIX + component, message)
    }

    /** Recoverable problems, e.g. "No network, retry scheduled". */
    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(PREFIX + component, message, throwable)
    }

    /** Failures, e.g. "Firestore write failed". Pass the exception for the stack trace. */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e(PREFIX + component, message, throwable)
    }

    /**
     * Returns a safe-to-log version of an identifier: only the last four characters.
     * Use this instead of logging a full Firebase UID or incident ID.
     */
    fun mask(id: String?): String =
        if (id.isNullOrBlank()) "none" else "***" + id.takeLast(4)
}

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

# References

Keep only the sources you actually used, and fill in the access date. Harvard style; adjust to your institution's guide.

Android Developers (2024) *Log*. Available at: https://developer.android.com/reference/android/util/Log (Accessed: 05/11/2026).

Android Developers (2024) *Save data in a local database using Room*. Available at: https://developer.android.com/training/data-storage/room (Accessed: 05/11/2026).

Android Developers (2024) *Schedule tasks with WorkManager*. Available at: https://developer.android.com/topic/libraries/architecture/workmanager (Accessed: 04/11/2026).

Android Developers (2024) *Show a biometric authentication dialog*. Available at: https://developer.android.com/identity/sign-in/biometric-auth (Accessed: 05/11/2026).

Firebase (2024) *Authenticate with Google on Android*. Available at: https://firebase.google.com/docs/auth/android/google-signin (Accessed: 04/11/2026).

Firebase (2024) *Get realtime updates with Cloud Firestore*. Available at: https://firebase.google.com/docs/firestore/query-data/listen (Accessed: 22/09/2026).

Firebase (2024) *Verify ID tokens*. Available at: https://firebase.google.com/docs/auth/admin/verify-id-tokens (Accessed: 05/11/2026).

Square (2024) *Retrofit*. Available at: https://square.github.io/retrofit/ (Accessed: 22/09/2026).

VMware / Broadcom (2024) *Spring Boot*. Available at: https://spring.io/projects/spring-boot (Accessed: 05/11/2026).

## AI tools

AI Usage Declaration – SafeConnect Application

Artificial Intelligence (AI) tools were used as supporting resources during the development of the SafeConnect mobile application.

AI assistance was used to help with:

Understanding Kotlin and Android Studio development concepts.

Identifying and troubleshooting programming errors.

Improving Firebase Authentication, Firestore and application integration.

Developing and reviewing input validation and unit testing approaches.

Improving the application's user interface and navigation.

Understanding GitHub Actions and automated build workflows.

Preparing technical documentation and the project README file.

AI-generated suggestions and code were reviewed, adapted and tested during development. The developer remained responsible for the application's implementation, integration, testing and final decisions.

AI was used as a development and learning assistant rather than as a replacement for independent programming, understanding or testing.

All submitted functionality should be verified by running the application and its relevant tests. Any AI-assisted material included in the final submission should be acknowledged in accordance with the institution's academic integrity requirements.
