const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { createFirestoreStore } = require("./src/store");
const { createApp } = require("./src/app");

admin.initializeApp();

const app = createApp({
  store: createFirestoreStore(admin.firestore()),
  // Production token check: the Firebase Admin SDK validates the ID token's
  // signature, expiry and audience against our project.
  verifyToken: async (idToken) => {
    const decoded = await admin.auth().verifyIdToken(idToken);
    return { uid: decoded.uid, email: decoded.email };
  },
});

// Deployed as one HTTPS function named "api". After `firebase deploy` the CLI
// prints its URL; that URL (plus a trailing slash) is the Android BASE_URL.
exports.api = onRequest({ region: "us-central1" }, app);
