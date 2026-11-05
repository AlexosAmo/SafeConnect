const express = require("express");
const cors = require("cors");
const { createAuth } = require("./middleware/auth");
const { createUserRouter } = require("./routes/users");
const { createIncidentRouter } = require("./routes/incidents");
const { createContactRouter } = require("./routes/contacts");

/**
 * Builds the SafeConnect REST API.
 *  - store:       data access (Firestore in production, in-memory fake in tests)
 *  - verifyToken: async (idToken) => { uid, email } — firebase-admin in production
 */
function createApp({ store, verifyToken }) {
  const app = express();
  app.use(cors());
  app.use(express.json());

  const auth = createAuth(verifyToken);

  app.get("/", (_req, res) => res.json({ status: "ok", service: "SafeConnect API" }));
  app.use("/users", createUserRouter(store, auth));
  app.use("/incidents", createIncidentRouter(store, auth));
  app.use("/contacts", createContactRouter(store, auth));

  app.use((_req, res) => res.status(404).json({ message: "Not found" }));
  return app;
}

module.exports = { createApp };
