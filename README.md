# SafeConnect

Android-first community safety application built with Kotlin, Firebase and an independent Spring Boot API.

## Current architecture

- **Android:** Kotlin, AndroidX, XML layouts, Retrofit.
- **Identity:** Firebase Authentication. The Spring Boot API verifies Firebase ID tokens; it does not store user passwords.
- **Cloud data:** Cloud Firestore remains the live cloud database. Firebase Storage and Firebase Cloud Messaging remain enabled.
- **Offline outbox:** Room stores locally created incident reports; WorkManager retries delivery when a network is available.
- **Independent backend:** Spring Boot REST API on Azure App Service. The API uses Firebase Admin SDK to verify identity and writes synchronized incident records to Firestore.

Firebase is deliberately retained. The REST API is an additional trusted service layer, not a replacement for Firebase SDK functionality.

## Open in Android Studio

1. Extract the repository and open the `SafeConnect.app` root folder (the folder containing `settings.gradle.kts`).
2. Keep your own `app/google-services.json` in place. Never commit service-account JSON files or private keys.
3. Allow Gradle to sync and install the Android SDK versions requested by the project.
4. The API base URL is configurable. For a local Spring Boot API running on your development computer and an Android emulator, run Gradle with `-PsafeConnectApiBaseUrl=http://10.0.2.2:8080/`.
5. For Azure, set `-PsafeConnectApiBaseUrl=https://<your-app-name>.azurewebsites.net/` or configure the same Gradle property in your local Gradle properties. Do not commit production secrets.

The default API URL is intentionally a placeholder until your Azure App Service is created.

## Run the Spring Boot API locally

Requirements: JDK 21 and Gradle 8.14.3 or later compatible with the Spring Boot plugin.

Configure Application Default Credentials for Firebase Admin SDK and set `FIREBASE_PROJECT_ID` to your Firebase project ID. Do not put a service-account key in source control. Then run:

```bash
cd backend
gradle bootRun
```

Health check: `GET http://localhost:8080/actuator/health`.

The initial API endpoint is `POST /api/v1/incidents`. It requires a Firebase ID token in `Authorization: Bearer <token>` and an `Idempotency-Key` header equal to the stable client incident ID.

## Offline incident behavior

1. Save a report in Room before attempting network delivery.
2. WorkManager waits for a connected network.
3. The worker obtains the current Firebase ID token and sends the outbox record to Spring Boot.
4. Spring Boot verifies the token and writes to the existing Firestore `incidents` collection.
5. The Room row is marked `SYNCED` only after a successful API response. A stable incident ID makes retries idempotent.

Do not interpret a locally queued SOS as delivered. The UI must distinguish **Queued**, **Sent**, and **Confirmed** states. The current implementation provides the durable outbox and API sync foundation; emergency delivery confirmation and contact-provider integration are subsequent safety-critical work. The live community feed now subscribes to Firestore snapshot updates while its screen is open.

## Five user-configurable safety options

The initial preference model supports: nearby-alert radius, automatic journey sharing, journey check-in interval, community-feed category filters, and an optional discreet SOS shortcut. A dedicated Settings screen now saves these preferences locally. The discreet SOS control is a preference only; the activation gesture is not yet active.

## Languages

Existing English, Afrikaans, isiZulu and Sesotho resources remain. Sepedi, Setswana and isiXhosa locale codes and starter resources are added. Review and professionally translate all user-facing strings before publication; untranslated strings fall back to English.

## Tests

- Android unit tests: `./gradlew :app:testDebugUnitTest`
- Android instrumented tests: `./gradlew :app:connectedDebugAndroidTest`
- Backend tests: `cd backend && gradle test`
- GitHub Actions runs Android unit tests and backend tests on pushes and pull requests.

## Security and release notes

- Use HTTPS in production and restrict CORS to the approved clients if browser clients are later introduced.
- Never log tokens, report bodies, personal details or precise location data.
- Keep Firebase Firestore/Storage rules restrictive and test them with the Firebase Emulator Suite.
- Configure Azure managed identity / workload identity or securely mounted Application Default Credentials for Firebase Admin. Do not commit service-account keys.
- Add a production privacy policy, retention policy, consent flows and Play Console Data Safety declaration.
- Request background location only after a clear user-facing explanation and only when the journey-tracking feature is active.
- Silent SOS must be explicitly configured, cancellable with safeguards, and show truthful queued/sent/confirmed delivery states. No offline device can guarantee that an emergency message reached another person.

## Project status

This repository contains the Phase 1 architecture foundation and initial offline incident synchronization slice. Google/Facebook SSO, production journey tracking, the silent SOS activation UX, AI provider integration, full translations, and Play Store release signing are not represented as complete; they are tracked in `docs/IMPLEMENTATION_ROADMAP.md`.

## Phase 2 – Advanced authentication

See [`docs/PHASE_2_AUTHENTICATION.md`](docs/PHASE_2_AUTHENTICATION.md) for the Google/Facebook Firebase SSO setup, biometric unlock behaviour, Spring Boot token verification and the device test checklist. Google OAuth and Facebook App ID/client-token values must be configured by the project owner before those provider buttons can authenticate users.


## Phase 3 – Offline-first

See [docs/PHASE_3_OFFLINE_FIRST.md](docs/PHASE_3_OFFLINE_FIRST.md) for Room cache, durable outbox, WorkManager retry, migration, privacy boundaries, and verification steps.

## Phase 4 – Advanced safety features

See [`docs/PHASE_4_SAFETY_FEATURES.md`](docs/PHASE_4_SAFETY_FEATURES.md) for live journey tracking, linked-contact sharing, discreet in-app SOS, FCM notification dispatch, Azure backend configuration, privacy controls and the two-device test plan.

## Phase 7 – Profile, community, assessment, SOS and themes

This source update extends the existing Firebase-backed Android app with:

- Firebase Google ID-token sign-in validation and removal of the Facebook SDK/login integration.
- Editable profile name, email, phone, province and profile photo; deterministic SafeConnect ID (`SC-` plus the Firebase UID).
- Province-aware, online-only live community reports. New public reports contain no precise latitude/longitude or reporter UID.
- An eight-question self-reported safety readiness assessment, private Firestore history and latest-result display.
- A redesigned Safety Score and Safety AI screen; the AI request has a 15-second timeout and a local guidance fallback.
- Opt-in direct SOS SMS through the device SMS service, plus sent/delivery status callbacks.
- Journey completion while keeping the live location-sharing service running, until the user separately stops sharing.
- Light/Dark palettes and Room database migration 4 → 5 for province fields.

### Build and test

From the project root in Android Studio Terminal (Windows PowerShell):

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK, if the build succeeds, is created at `app/build/outputs/apk/debug/app-debug.apk`.

### Firebase deployment

After reviewing the rules, deploy them to the Firebase project selected by `.firebaserc`:

```powershell
firebase deploy --only firestore:rules,storage
```

Do not deploy rules to production until you have tested the profile, emergency contact, incident, journey, community and assessment flows using the Firebase Emulator Suite.

### Google Sign-In

Enable Google under Firebase Authentication → Sign-in method, register the debug and release SHA-1/SHA-256 certificate fingerprints under Project settings → Your apps → Android, and replace `app/google-services.json` with the refreshed file. The OAuth Web client ID must be present as `default_web_client_id`.

### SMS release warning

Direct SMS requires the user to opt in and grant `SEND_SMS`. Google Play restricts SMS permissions to eligible core-use cases and may reject an app that does not qualify. For a Play Store release, use a server-side SMS provider approved for your use case and keep its credentials on the server; do not embed provider secrets in the APK.

See `docs/PHASE_7_IMPLEMENTATION.md` for security, privacy and validation notes.
