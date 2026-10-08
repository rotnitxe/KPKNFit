import {Timestamp} from 'firebase-admin/firestore';
import {randomUUID} from 'node:crypto';
import {digest, fail, isPro, validatePurchaseSnapshot} from './protocol.mjs';

/** Play is the source of truth; client status/price/expiry are never trusted. */
export class BillingService {
  constructor(database, play, productId = 'kpkn_pro', clock = () => Date.now()) {
    this.db = database; this.play = play; this.productId = productId; this.clock = clock;
  }
  async verify(uid, token, eventId = null, clientPurchase = true) {
    if (typeof token !== 'string' || token.length < 4 || token.length > 4096) fail('invalid-argument', 'Invalid purchase token');
    const accountRef = this.db.doc(`accounts/${uid}`);
    const tokenRef = this.db.doc(`purchaseBindings/${digest(token)}`);
    const refreshId = randomUUID();
    await this.db.runTransaction(async transaction => {
      const [account, binding] = await transaction.getAll(accountRef, tokenRef);
      if (account.data()?.state !== 'ACTIVE') fail('permission-denied', 'Account is unavailable');
      if (binding.exists && binding.data().ownerHash && binding.data().ownerHash !== digest(uid)) fail('permission-denied', 'Purchase is bound to another account');
      if (binding.data()?.refreshUntilEpochMs > this.clock()) fail('resource-exhausted', 'Purchase validation is already in progress');
      if (clientPurchase) {
        const window = Math.floor(this.clock() / 60_000);
        const count = account.data().billingWindow === window ? account.data().billingCount : 0;
        if (count >= 12) fail('resource-exhausted', 'Purchase validation rate limit reached');
        transaction.update(accountRef, {billingWindow: window, billingCount: count + 1});
      }
      transaction.set(tokenRef, {refreshId, refreshUntilEpochMs: this.clock() + 90_000}, {merge: true});
    });
    try {
    let snapshot;
    try { snapshot = await this.play.get(token); }
    catch (error) {
      if (error?.code !== 'not-found') throw error;
      const previous = (await tokenRef.get()).data();
      if (previous?.ownerUid !== uid) throw error;
      return await this.db.runTransaction(async transaction => {
        const entitlementRef = this.db.doc(`entitlements/${uid}`);
        const [account, entitlement, binding] = await transaction.getAll(accountRef, entitlementRef, tokenRef);
        if (account.data()?.state !== 'ACTIVE' || binding.data()?.refreshId !== refreshId) fail('permission-denied', 'Account is unavailable');
        const expired = {level: 'FREE', state: 'REVOKED', validUntilEpochMs: 0, confirmedAtEpochMs: this.clock()};
        transaction.update(tokenRef, expired);
        if (entitlement.data()?.purchaseHash === digest(token)) transaction.set(entitlementRef,
          {...expired, productId: this.productId, purchaseHash: digest(token), validUntil: Timestamp.fromMillis(0)});
        const effective = entitlement.data()?.purchaseHash === digest(token) ? expired : entitlement.data() ?? expired;
        const {validUntil, purchaseHash, ...result} = effective;
        return result;
      });
    }
    const state = validatePurchaseSnapshot(snapshot, {productId: this.productId, accountBinding: digest(uid), now: this.clock()});
    const linkedRef = snapshot.linkedPurchaseToken ? this.db.doc(`purchaseBindings/${digest(snapshot.linkedPurchaseToken)}`) : null;
    const eventRef = eventId ? this.db.doc(`billingEvents/${digest(eventId)}`) : null;
    const entitlementRef = this.db.doc(`entitlements/${uid}`);
    const effective = await this.db.runTransaction(async transaction => {
      const refs = [accountRef, tokenRef, entitlementRef, ...(linkedRef ? [linkedRef] : []), ...(eventRef ? [eventRef] : [])];
      const records = await transaction.getAll(...refs);
      if (records[0].data()?.state !== 'ACTIVE') fail('permission-denied', 'Account is unavailable');
      if (records[1].data()?.refreshId !== refreshId) fail('aborted', 'A newer purchase validation took ownership');
      for (const row of [records[1], ...(linkedRef ? [records[3]] : [])]) {
        if (row.exists && row.data().ownerHash && row.data().ownerHash !== digest(uid)) fail('permission-denied', 'Purchase is bound to another account');
      }
      const binding = {ownerUid: uid, ownerHash: digest(uid), productId: this.productId, token,
        state: state.state, confirmedAtEpochMs: state.confirmedAtEpochMs, validUntilEpochMs: state.validUntilEpochMs};
      transaction.set(tokenRef, binding, {merge: true});
      const current = records[2].data();
      let confirmed = current;
      if (!isPro(current, this.clock()) || current.purchaseHash === digest(token) ||
          (state.level === 'PRO' && state.validUntilEpochMs >= current.validUntilEpochMs)) {
        confirmed = {...state, productId: this.productId, validUntil: Timestamp.fromMillis(state.validUntilEpochMs), purchaseHash: digest(token)};
        transaction.set(entitlementRef, confirmed);
      }
      if (eventRef) transaction.set(eventRef, {processedAtEpochMs: this.clock(), purchaseHash: digest(token)});
      const {validUntil, purchaseHash, ...result} = confirmed;
      return result;
    });
    // A receipt first makes acknowledgement retryable after network interruption.
    if (state.level === 'PRO' && snapshot.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') {
      await this.play.acknowledge(token, this.productId);
      await tokenRef.update({acknowledged: true});
    }
    return effective;
    } finally {
      await this.db.runTransaction(async transaction => {
        const current = await transaction.get(tokenRef);
        if (current.data()?.refreshId === refreshId) transaction.update(tokenRef, {refreshUntilEpochMs: 0});
      });
    }
  }
  async handleNotification({eventId, packageName, expectedPackage, purchaseToken}) {
    if (packageName !== expectedPackage || !eventId) fail('invalid-argument', 'Invalid Play notification');
    const ref = this.db.doc(`purchaseBindings/${digest(purchaseToken ?? '')}`);
    const binding = (await ref.get()).data();
    if (!binding?.ownerUid) return {status: 'UNBOUND'};
    const account = (await this.db.doc(`accounts/${binding.ownerUid}`).get()).data();
    if (account?.state !== 'ACTIVE') return {status: 'DELETED_ACCOUNT'};
    // Even a repeated or reordered RTDN reads the CURRENT Play state, preventing stale revival.
    const result = await this.verify(binding.ownerUid, purchaseToken, eventId, false);
    return {status: 'CONFIRMED', state: result.state};
  }
}
