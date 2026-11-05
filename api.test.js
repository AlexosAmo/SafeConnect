const test = require("node:test");
const assert = require("node:assert/strict");
const request = require("supertest");
const { createApp } = require("../src/app");
const { createFakeStore, fakeVerifyToken } = require("./fakeStore");

function setup() {
  const store = createFakeStore();
  const app = createApp({ store, verifyToken: fakeVerifyToken });
  const as = (uid) => ({ Authorization: `Bearer token-${uid}` });
  return { app, store, as };
}

const profileBody = { fullName: "Thabo Nkosi", phoneNumber: "0821234567" };

// ---------------------------------------------------------------- auth guard
test("health check is public", async () => {
  const { app } = setup();
  const res = await request(app).get("/");
  assert.equal(res.status, 200);
  assert.equal(res.body.status, "ok");
});

test("every data route rejects requests with no token", async () => {
  const { app } = setup();
  for (const [method, path] of [["get", "/users/u1"], ["post", "/users"], ["get", "/incidents"],
                                ["post", "/incidents"], ["get", "/contacts"], ["post", "/contacts"]]) {
    const res = await request(app)[method](path);
    assert.equal(res.status, 401, `${method.toUpperCase()} ${path}`);
  }
});

test("an invalid token is rejected", async () => {
  const { app } = setup();
  const res = await request(app).get("/incidents").set("Authorization", "Bearer garbage");
  assert.equal(res.status, 401);
});

test("a malformed Authorization header is rejected", async () => {
  const { app } = setup();
  const res = await request(app).get("/incidents").set("Authorization", "Token abc");
  assert.equal(res.status, 401);
});

// ------------------------------------------------------------------- profile
test("creates a profile for the signed-in user, using the token's email", async () => {
  const { app, as } = setup();
  const res = await request(app).post("/users").set(as("u1")).send(profileBody);
  assert.equal(res.status, 201);
  assert.equal(res.body.userId, "u1");
  assert.equal(res.body.email, "u1@example.com");
  assert.equal(res.body.preferredLanguage, "en");
  assert.equal(res.body.safetyScore, 100);
  assert.equal(res.body.notificationsEnabled, true);
});

test("creating a profile twice returns 409", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const res = await request(app).post("/users").set(as("u1")).send(profileBody);
  assert.equal(res.status, 409);
});

test("profile creation validates its input", async () => {
  const { app, as } = setup();
  const bad = [
    { ...profileBody, fullName: "   " },
    { ...profileBody, phoneNumber: "123" },
    { ...profileBody, preferredLanguage: "xx" },
  ];
  for (const body of bad) {
    const res = await request(app).post("/users").set(as("u1")).send(body);
    assert.equal(res.status, 400, JSON.stringify(body));
  }
});

test("a user can read their own profile but not someone else's", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  assert.equal((await request(app).get("/users/u1").set(as("u1"))).status, 200);
  assert.equal((await request(app).get("/users/u1").set(as("u2"))).status, 403);
});

test("reading a profile that was never created returns 404", async () => {
  const { app, as } = setup();
  assert.equal((await request(app).get("/users/u1").set(as("u1"))).status, 404);
});

test("settings update persists language code and notification flag", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const res = await request(app).put("/users/u1").set(as("u1"))
    .send({ preferredLanguage: "zu", notificationsEnabled: false });
  assert.equal(res.status, 200);
  assert.equal(res.body.preferredLanguage, "zu");
  assert.equal(res.body.notificationsEnabled, false);
  const reread = await request(app).get("/users/u1").set(as("u1"));
  assert.equal(reread.body.preferredLanguage, "zu");
});

test("legacy language labels are normalised to codes", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const res = await request(app).put("/users/u1").set(as("u1")).send({ preferredLanguage: "Sesotho" });
  assert.equal(res.body.preferredLanguage, "st");
});

test("settings update rejects bad values and empty bodies", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const bad = [{ preferredLanguage: "klingon" }, { notificationsEnabled: "yes" }, { fullName: "" }, { phoneNumber: "1" }, {}];
  for (const body of bad) {
    const res = await request(app).put("/users/u1").set(as("u1")).send(body);
    assert.equal(res.status, 400, JSON.stringify(body));
  }
});

test("a user cannot update another user's profile", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const res = await request(app).put("/users/u1").set(as("u2")).send({ notificationsEnabled: false });
  assert.equal(res.status, 403);
});

test("protected fields such as safetyScore cannot be set through the settings endpoint", async () => {
  const { app, as } = setup();
  await request(app).post("/users").set(as("u1")).send(profileBody);
  const res = await request(app).put("/users/u1").set(as("u1")).send({ notificationsEnabled: false, safetyScore: 9999 });
  assert.equal(res.status, 200);
  assert.equal(res.body.safetyScore, 100);
});

// ----------------------------------------------------------------- incidents
test("an SOS is created Active with the server deciding status and defaults", async () => {
  const { app, as } = setup();
  const res = await request(app).post("/incidents").set(as("u1"))
    .send({ type: "sos", latitude: -25.74, longitude: 28.19, status: "Resolved" });
  assert.equal(res.status, 201);
  assert.equal(res.body.status, "Active");           // client-supplied status is ignored
  assert.equal(res.body.category, "Emergency");
  assert.equal(res.body.responderStatus, "Alert sent");
  assert.equal(res.body.userId, "u1");
  assert.ok(res.body.id);
});

test("a report is created as Submitted", async () => {
  const { app, as } = setup();
  const res = await request(app).post("/incidents").set(as("u1"))
    .send({ type: "report", category: "Theft", description: "Bag stolen" });
  assert.equal(res.status, 201);
  assert.equal(res.body.status, "Submitted");
  assert.equal(res.body.latitude, null);             // location is optional
});

test("incident creation validates type and coordinates", async () => {
  const { app, as } = setup();
  const bad = [{ type: "banana" }, { latitude: 100, longitude: 0 }, { latitude: 10 }, { latitude: "a", longitude: "b" }];
  for (const body of bad) {
    const res = await request(app).post("/incidents").set(as("u1")).send(body);
    assert.equal(res.status, 400, JSON.stringify(body));
  }
});

test("incident list returns only the caller's incidents, newest first", async () => {
  const { app, as } = setup();
  await request(app).post("/incidents").set(as("u1")).send({ type: "report", description: "first" });
  await new Promise((r) => setTimeout(r, 5));
  await request(app).post("/incidents").set(as("u1")).send({ type: "report", description: "second" });
  await request(app).post("/incidents").set(as("u2")).send({ type: "report", description: "other user" });

  const res = await request(app).get("/incidents").set(as("u1"));
  assert.equal(res.status, 200);
  assert.deepEqual(res.body.map((i) => i.description), ["second", "first"]);
});

test("a user can fetch their own incident but another user gets 404", async () => {
  const { app, as } = setup();
  const created = await request(app).post("/incidents").set(as("u1")).send({ type: "sos" });
  const id = created.body.id;
  assert.equal((await request(app).get(`/incidents/${id}`).set(as("u1"))).status, 200);
  assert.equal((await request(app).get(`/incidents/${id}`).set(as("u2"))).status, 404);
});

test("cancelling an SOS updates its status", async () => {
  const { app, as } = setup();
  const { body } = await request(app).post("/incidents").set(as("u1")).send({ type: "sos" });
  const res = await request(app).patch(`/incidents/${body.id}`).set(as("u1")).send({ status: "Cancelled" });
  assert.equal(res.status, 200);
  assert.equal(res.body.status, "Cancelled");
});

test("clients cannot set arbitrary statuses or change someone else's incident", async () => {
  const { app, as } = setup();
  const { body } = await request(app).post("/incidents").set(as("u1")).send({ type: "sos" });
  assert.equal((await request(app).patch(`/incidents/${body.id}`).set(as("u1")).send({ status: "Responder arrived" })).status, 400);
  assert.equal((await request(app).patch(`/incidents/${body.id}`).set(as("u2")).send({ status: "Cancelled" })).status, 404);
  const still = await request(app).get(`/incidents/${body.id}`).set(as("u1"));
  assert.equal(still.body.status, "Active");
});

test("responder updates written server-side are visible when the app polls the incident", async () => {
  const { app, store, as } = setup();
  const { body } = await request(app).post("/incidents").set(as("u1")).send({ type: "sos" });
  await store.updateIncident(body.id, { responderName: "Sipho", responderStatus: "En route", responderLatitude: -25.7, responderLongitude: 28.2 });
  const res = await request(app).get(`/incidents/${body.id}`).set(as("u1"));
  assert.equal(res.body.responderName, "Sipho");
  assert.equal(res.body.responderStatus, "En route");
  assert.equal(res.body.responderLatitude, -25.7);
});

// ------------------------------------------------------------------ contacts
test("contacts: add, list (sorted by name), delete", async () => {
  const { app, as } = setup();
  await request(app).post("/contacts").set(as("u1")).send({ name: "Zanele", phone: "0829999999", relationship: "Sister" });
  await request(app).post("/contacts").set(as("u1")).send({ name: "Anele", phone: "0821111111" });

  const list = await request(app).get("/contacts").set(as("u1"));
  assert.deepEqual(list.body.map((c) => c.name), ["Anele", "Zanele"]);
  assert.equal(list.body[0].relationship, "Emergency contact"); // default

  const del = await request(app).delete(`/contacts/${list.body[0].id}`).set(as("u1"));
  assert.equal(del.status, 204);
  assert.equal((await request(app).get("/contacts").set(as("u1"))).body.length, 1);
});

test("contacts are private to each user", async () => {
  const { app, as } = setup();
  const created = await request(app).post("/contacts").set(as("u1")).send({ name: "Mum", phone: "0821234567" });
  assert.deepEqual((await request(app).get("/contacts").set(as("u2"))).body, []);
  assert.equal((await request(app).delete(`/contacts/${created.body.id}`).set(as("u2"))).status, 404);
});

test("contact creation requires a name and a valid phone", async () => {
  const { app, as } = setup();
  for (const body of [{ name: "", phone: "0821234567" }, { name: "Mum", phone: "12" }, {}]) {
    const res = await request(app).post("/contacts").set(as("u1")).send(body);
    assert.equal(res.status, 400, JSON.stringify(body));
  }
});

test("unknown routes return 404 JSON", async () => {
  const { app } = setup();
  const res = await request(app).get("/nope");
  assert.equal(res.status, 404);
  assert.equal(res.body.message, "Not found");
});
