import {digest, fail} from './protocol.mjs';

/** The first transaction blocks old devices even if a later purge step must retry. */
export class DeletionService {
  constructor(database, auth, bucket, play, clock = () => Date.now()) {
    this.db = database; this.auth = auth; this.bucket = bucket; this.play = play; this.clock = clock;
  }
  async request(uid, authTime, confirmation) {
    if (confirmation !== 'DELETE_ACCOUNT' || !Number.isFinite(authTime) ||
        authTime * 1000 < this.clock() - 5 * 60_000 || authTime * 1000 > this.clock() + 30_000) fail('failed-precondition', 'Recent authentication and confirmation are required');
    await this.db.runTransaction(async transaction => {
      const ref = this.db.doc(`accounts/${uid}`);
      const current = (await transaction.get(ref)).data();
      if (!current) {
        transaction.set(ref, {state: 'DELETING', generation: 1, generationText: '1', deletionRequestedAtEpochMs: this.clock()});
        return;
      }
      if (current.state === 'DELETED') return;
      if (current.state === 'ACTIVE') {
        const generation = current.generation + 1;
        transaction.update(ref, {state: 'DELETING', generation, generationText: String(generation),
          deletionRequestedAtEpochMs: this.clock()});
      }
    });
    try { await this.resume(uid); return {status: 'DELETED'}; }
    catch { return {status: 'DELETION_PENDING'}; } // Durable request already blocks uploads; scheduled retries finish the purge.
  }
  async resume(uid) {
    const ref = this.db.doc(`accounts/${uid}`);
    const account = (await ref.get()).data();
    if (!account || account.state === 'DELETED') return;
    if (account.state !== 'DELETING') fail('failed-precondition', 'Deletion was not requested');
    try {
      await this.auth.revokeRefreshTokens(uid);
    } catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
    const bindings = await this.db.collection('purchaseBindings').where('ownerUid', '==', uid).get();
    for (const binding of bindings.docs) {
      const data = binding.data();
      // Cancellation is explicit; account deletion does not silently leave renewals running.
      if (!data.renewalsStopped && ['ACTIVE', 'GRACE', 'CANCELED', 'ON_HOLD', 'PAUSED'].includes(data.state)) {
        try { await this.play.cancel(data.token); }
        catch (error) { if (error?.code !== 'not-found') throw error; }
        await binding.ref.update({renewalsStopped: true});
      }
    }
    await this.bucket.deleteFiles({prefix: `accounts/${uid}/`, force: true});
    for (const collection of await ref.listCollections()) await this.db.recursiveDelete(collection);
    await this.db.doc(`entitlements/${uid}`).delete();
    // Global operational receipts are outside the account subtree and must be purged too.
    for (const binding of bindings.docs) {
      const events = await this.db.collection('billingEvents').where('purchaseHash', '==', binding.id).get();
      for (const event of events.docs) await event.ref.delete();
    }
    const guestBindings = await this.db.collection('guestBindings').where('ownerHash', '==', digest(uid)).get();
    for (const guest of guestBindings.docs) await guest.ref.set({ownerHash: digest(uid), state: 'DELETED_ACCOUNT',
      deletedAtEpochMs: this.clock()}); // No snapshot/import identity remains; prevents an old guest from changing owners.
    for (const binding of bindings.docs) {
      const data = binding.data();
      // Retain only a one-way ownership tombstone to prevent rebinding a purchase after deletion.
      await binding.ref.set({ownerHash: digest(uid), state: 'DELETED_ACCOUNT', purchaseHash: binding.id,
        renewalsStopped: true, deletedAtEpochMs: this.clock()});
    }
    try { await this.auth.deleteUser(uid); }
    catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
    await ref.set({state: 'DELETED', generation: account.generation, generationText: String(account.generation),
      deletionCompletedAtEpochMs: this.clock()});
  }
  async retryPending() {
    const pending = await this.db.collection('accounts').where('state', '==', 'DELETING').limit(20).get();
    for (const account of pending.docs) {
      try { await this.resume(account.id); }
      catch { /* Keep DELETING and retry next time. Never reactivate on partial failure. */ }
    }
  }
}
