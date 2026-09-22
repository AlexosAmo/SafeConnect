# Implementation Plan - Profile Shortcut and Language Persistence

Enhance user navigation by making the profile icon a shortcut to settings and ensure language preferences are correctly persisted from registration onwards.

## Proposed Changes

### Home Screen

#### [MODIFY] [HomeActivity](file:///C:/Users/Amoge/AndroidStudioProjects/SafeConnect.app/app/src/main/java/com/amogelang/safeconnect/app/MainActivity.kt)
- Set an `OnClickListener` on `ivProfileIcon` to navigate to `SettingsActivity`.
- Refine `loadProfile` to avoid unnecessary locale reapplications if the current locale matches the profile.

### Registration

#### [MODIFY] [RegisterActivity](file:///C:/Users/Amoge/AndroidStudioProjects/SafeConnect.app/app/src/main/java/com/amogelang/safeconnect/app/MainActivity.kt)
- Update the `register` call to use the current app's locale (set during Splash) instead of defaulting to "en". This ensures the user's initial preference is saved to their Firebase profile.

### Data Repository

#### [MODIFY] [FirebaseRepository](file:///C:/Users/Amoge/AndroidStudioProjects/SafeConnect.app/app/src/main/java/com/amogelang/safeconnect/app/firebase/FirebaseRepository.kt)
- Update the `register` function signature to accept `preferredLanguage`.

## Verification Plan

### Automated Tests
- Build verification.

### Manual Verification
1. **Language Persistence**:
    - Start app, select "isiZulu" in Splash.
    - Register a new account.
    - Verify that the app stays in isiZulu after registration and login.
2. **Profile Shortcut**:
    - On the Home screen, tap the profile icon.
    - Verify it opens the Settings screen directly.
