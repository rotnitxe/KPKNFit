import {initializeApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
import {getAuth} from 'firebase-admin/auth';
import {getStorage} from 'firebase-admin/storage';
import {onCall, HttpsError} from 'firebase-functions/v2/https';
import {onMessagePublished} from 'firebase-functions/v2/pubsub';
import {onSchedule} from 'firebase-functions/v2/scheduler';
import {onObjectFinalized} from 'firebase-functions/v2/storage';
import {defineString} from 'firebase-functions/params';
import {ProtocolError, fail} from './protocol.mjs';
import {SyncService} from './sync-service.mjs';
import {BillingService} from './billing-service.mjs';
import {DeletionService} from './deletion-service.mjs';
import {PlayClient} from './play-client.mjs';
import {AccountObjectGuard} from './account-object-guard.mjs';

initializeApp();
const db = getFirestore();
const auth = getAuth();
const packageName = defineString('PLAY_PACKAGE_NAME', {default: 'com.example.kpkn'});
const product = defineString('PLAY_PRO_PRODUCT_ID', {default: 'kpkn_pro'});
const region = 'us-central1';
const limits = {region, maxInstances: 2, concurrency: 4, memory: '512MiB', timeoutSeconds: 120};
const callableOptions = {...limits, enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== 'true'};
const sync = new SyncService(db, () => Date.now(), getStorage().bucket());
const billing = () => new BillingService(db, new PlayClient(packageName.value()), product.value());
const deletion = () => new DeletionService(db, auth, getStorage().bucket(), new PlayClient(packageName.value()));
const uidOf = request => {
  if (!request.auth?.uid) fail('unauthenticated', 'Sign in to continue');
  return request.auth.uid;
};
const callable = handler => onCall(callableOptions, async request => {
  try { return {...await handler(request, uidOf(request)), serverTimeEpochMs: Date.now()}; }
  catch (error) {
    if (error instanceof ProtocolError) throw new HttpsError(error.code, error.message);
    // Never expose Google SDK objects, payloads, identity tokens or health data in errors/logs.
    throw new HttpsError('unavailable', 'The operation could not be completed. Retry safely.');
  }
});

export const openAccount = callable((_request, uid) => sync.openAccount(uid));
export const pushChanges = callable(async (request, uid) => {
  const operations = request.data?.operations;
  if (!Array.isArray(operations) || operations.length < 1 || operations.length > 8) fail('invalid-argument', 'Send one to eight operations');
  if (Buffer.byteLength(JSON.stringify(operations)) > 4 * 1024 * 1024) fail('invalid-argument', 'Batch is too large');
  const receipts = [];
  for (const operation of operations) receipts.push(await sync.push(uid, operation));
  return {schemaVersion: 1, receipts};
});
export const pullChanges = callable((request, uid) => sync.changes(uid, request.data?.generation,
  request.data?.after ?? 0, request.data?.limit ?? 50));
export const listConflicts = callable(async (request, uid) => ({conflicts: await sync.conflictPage(uid, request.data?.generation)}));
export const cancelRemoteWorkout = callable((request, uid) => sync.cancelRemoteWorkout(uid, request.data ?? {}));
export const retirePendingOperation = callable((request, uid) => sync.retirePendingOperation(uid, request.data?.operation));
export const resolveConflict = callable((request, uid) => sync.resolveConflict(uid, request.data?.generation,
  request.data?.conflictId, request.data?.operation, request.data?.resolution, request.data?.expectedRevision));
export const beginGuestImport = callable((request, uid) => sync.beginGuestImport(uid, request.data ?? {}));
export const finishGuestImport = callable((request, uid) => sync.finishGuestImport(uid, request.data?.importId));
export const readEntitlement = callable(async (_request, uid) => {
  const account = (await db.doc(`accounts/${uid}`).get()).data();
  if (account?.state !== 'ACTIVE') fail('permission-denied', 'Account is unavailable');
  const state = (await db.doc(`entitlements/${uid}`).get()).data();
  if (!state) return {level: 'FREE', state: 'NONE', validUntilEpochMs: 0, confirmedAtEpochMs: Date.now()};
  const {validUntil, purchaseHash, ...result} = state;
  return result;
});
export const validatePlayPurchase = callable((request, uid) => billing().verify(uid, request.data?.purchaseToken));
export const deleteAccount = callable((request, uid) => deletion().request(uid,
  request.auth.token.auth_time, request.data?.confirmation));
export const playNotifications = onMessagePublished({...limits, topic: 'kpkn-play-rtdn', retry: true}, async event => {
  const message = event.data.message;
  let payload;
  try { payload = message.json; }
  catch { return; }
  const purchaseToken = payload?.subscriptionNotification?.purchaseToken;
  if (!purchaseToken || payload.packageName !== packageName.value()) return;
  await billing().handleNotification({eventId: message.messageId, packageName: payload.packageName,
    expectedPackage: packageName.value(), purchaseToken});
});
export const retryAccountDeletion = onSchedule({...limits, schedule: 'every 15 minutes'}, () => deletion().retryPending());
export const purgeLateAccountUploads = onObjectFinalized({...limits, retry: true}, event =>
  new AccountObjectGuard(db, getStorage()).finalized(event.data));
