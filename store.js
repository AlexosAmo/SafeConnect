/**
 * Firestore data access, hidden behind a small interface so the routes never
 * touch the Firestore SDK directly (and tests can swap in an in-memory fake).
 *
 * Layout:  users/{uid}                          profile
 *          users/{uid}/emergencyContacts/{id}   contacts
 *          incidents/{id}                        SOS + reports (userId field = owner)
 */
function createFirestoreStore(db) {
  const users = () => db.collection("users");
  const incidents = () => db.collection("incidents");
  const contacts = (uid) => users().doc(uid).collection("emergencyContacts");

  return {
    async createUser(uid, data) {
      try {
        await users().doc(uid).create(data); // fails atomically if the profile already exists
      } catch (err) {
        const exists = err.code === 6 || err.code === "already-exists" || /already exists/i.test(err.message || "");
        if (exists) { const e = new Error("Profile exists"); e.code = "PROFILE_EXISTS"; throw e; }
        throw err;
      }
      return data;
    },

    async getUser(uid) {
      const doc = await users().doc(uid).get();
      return doc.exists ? doc.data() : null;
    },

    async updateUser(uid, fields) {
      const ref = users().doc(uid);
      const doc = await ref.get();
      if (!doc.exists) return null;
      await ref.update(fields);
      return { ...doc.data(), ...fields };
    },

    async createIncident(uid, data) {
      const ref = incidents().doc();
      const record = { id: ref.id, userId: uid, ...data };
      await ref.set(record);
      return record;
    },

    async getIncident(id) {
      const doc = await incidents().doc(id).get();
      return doc.exists ? doc.data() : null;
    },

    async listIncidents(uid) {
      // Sorted in memory so no composite Firestore index is needed.
      const snap = await incidents().where("userId", "==", uid).get();
      return snap.docs.map((d) => d.data()).sort((a, b) => b.createdAt - a.createdAt);
    },

    async updateIncident(id, fields) {
      const ref = incidents().doc(id);
      const doc = await ref.get();
      if (!doc.exists) return null;
      await ref.update(fields);
      return { ...doc.data(), ...fields };
    },

    async listContacts(uid) {
      const snap = await contacts(uid).get();
      return snap.docs.map((d) => d.data()).sort((a, b) => a.name.localeCompare(b.name));
    },

    async addContact(uid, data) {
      const ref = contacts(uid).doc();
      const record = { id: ref.id, ...data };
      await ref.set(record);
      return record;
    },

    async deleteContact(uid, id) {
      const ref = contacts(uid).doc(id);
      const doc = await ref.get();
      if (!doc.exists) return false;
      await ref.delete();
      return true;
    },
  };
}

module.exports = { createFirestoreStore };
