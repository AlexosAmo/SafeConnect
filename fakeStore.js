const { randomUUID } = require("node:crypto");

/** In-memory twin of src/store.js — same interface, no Firebase needed. */
function createFakeStore() {
  const users = new Map();
  const incidents = new Map();
  const contacts = new Map(); // uid -> Map(id -> contact)
  const bucket = (uid) => { if (!contacts.has(uid)) contacts.set(uid, new Map()); return contacts.get(uid); };

  return {
    async createUser(uid, data) {
      if (users.has(uid)) { const e = new Error("Profile exists"); e.code = "PROFILE_EXISTS"; throw e; }
      users.set(uid, { ...data });
      return data;
    },
    async getUser(uid) { return users.get(uid) || null; },
    async updateUser(uid, fields) {
      if (!users.has(uid)) return null;
      const merged = { ...users.get(uid), ...fields };
      users.set(uid, merged);
      return merged;
    },
    async createIncident(uid, data) {
      const record = { id: randomUUID(), userId: uid, ...data };
      incidents.set(record.id, record);
      return record;
    },
    async getIncident(id) { return incidents.get(id) || null; },
    async listIncidents(uid) {
      return [...incidents.values()].filter((i) => i.userId === uid).sort((a, b) => b.createdAt - a.createdAt);
    },
    async updateIncident(id, fields) {
      if (!incidents.has(id)) return null;
      const merged = { ...incidents.get(id), ...fields };
      incidents.set(id, merged);
      return merged;
    },
    async listContacts(uid) { return [...bucket(uid).values()].sort((a, b) => a.name.localeCompare(b.name)); },
    async addContact(uid, data) {
      const record = { id: randomUUID(), ...data };
      bucket(uid).set(record.id, record);
      return record;
    },
    async deleteContact(uid, id) { return bucket(uid).delete(id); },
    _incidents: incidents, // exposed so tests can simulate a responder updating an incident server-side
  };
}

/** Fake Firebase token check: "token-<uid>" is valid, anything else throws. */
async function fakeVerifyToken(token) {
  if (!token.startsWith("token-")) throw new Error("bad token");
  const uid = token.slice("token-".length);
  return { uid, email: `${uid}@example.com` };
}

module.exports = { createFakeStore, fakeVerifyToken };
