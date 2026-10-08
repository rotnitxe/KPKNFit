import test, {before, after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {initializeApp, deleteApp} from 'firebase-admin/app';
import {getFirestore, Timestamp} from 'firebase-admin/firestore';
import {getAuth} from 'firebase-admin/auth';
import {getStorage} from 'firebase-admin/storage';
import {initializeTestEnvironment, assertFails, assertSucceeds} from '@firebase/rules-unit-testing';
import {doc, getDoc, setDoc} from 'firebase/firestore';
import {ref, uploadBytes, getBytes} from 'firebase/storage';
import {SyncService} from '../src/sync-service.mjs';
import {BillingService} from '../src/billing-service.mjs';
import {DeletionService} from '../src/deletion-service.mjs';
import {AccountObjectGuard} from '../src/account-object-guard.mjs';
import {canonicalJson, digest} from '../src/protocol.mjs';

const projectId = 'demo-kpkn-stability';
const bucketName = `${projectId}.appspot.com`;
let app, database, auth, bucket, rules, sync;
before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST?.endsWith(':18080'), 'Local Firestore is required');
  assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST?.endsWith(':19099'), 'Local Auth is required');
  app = initializeApp({projectId, storageBucket: bucketName});
  database = getFirestore(app); auth = getAuth(app); bucket = getStorage(app).bucket(); sync = new SyncService(database, () => Date.now(), bucket);
  rules = await initializeTestEnvironment({projectId,
    firestore: {host: '127.0.0.1', port: 18080, rules: await readFile('../firestore.rules', 'utf8')},
    storage: {host: '127.0.0.1', port: 19199, rules: await readFile('../storage.rules', 'utf8')}});
});
after(async () => { await rules?.cleanup(); await deleteApp(app); });
const operation = (id, entityId = 'log-1', revision = 0) => ({schemaVersion: 1, operationId: id, entityId,
  deviceId: 'device-a', accountGeneration: 1, baseRevision: revision, kind: 'workout_log',
  changedAtEpochMs: Date.now(), zoneId: 'America/Santiago', unitSystem: 'METRIC', deleted: false,
  payload: {id: entityId, exerciseId: 'squat', weightKg: 70, repetitions: 8}});
const account = async () => { const uid = randomUUID(); await auth.createUser({uid}); await sync.openAccount(uid); return uid; };

test('authenticated private downloads resume with byte ranges and reject a different owner or deleted account', async () => {
  const register = async () => {
    const response = await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-only', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({returnSecureToken: true})});
    assert.equal(response.status, 200);
    const identity = await response.json();
    assert.ok(identity.idToken && identity.localId);
    await sync.openAccount(identity.localId);
    return identity;
  };
  const owner = await register(), other = await register();
  const bytes = Buffer.from(JSON.stringify({id: 'resumable-data', notes: 'private fixture '.repeat(10000)}));
  const hash = digest(bytes), path = `accounts/${owner.localId}/1/data/${hash}`;
  await bucket.file(path).save(bytes, {metadata: {contentType: 'application/json', metadata: {ownerUid: owner.localId, fileId: hash}}});
  const url = `http://127.0.0.1:19199/v0/b/${bucketName}/o/${encodeURIComponent(path)}?alt=media`;
  const offset = 65536;
  const response = await fetch(url, {headers: {Authorization: `Firebase ${owner.idToken}`, Range: `bytes=${offset}-`}});
  assert.equal(response.status, 206);
  assert.equal(response.headers.get('content-range'), `bytes ${offset}-${bytes.length - 1}/${bytes.length}`);
  const tail = Buffer.from(await response.arrayBuffer());
  assert.deepEqual(tail, bytes.subarray(offset));
  assert.equal(digest(Buffer.concat([bytes.subarray(0, offset), tail])), hash);
  const unauthorized = await fetch(url, {headers: {Authorization: `Firebase ${other.idToken}`, Range: `bytes=${offset}-`}});
  assert.ok([401, 403].includes(unauthorized.status));
  await unauthorized.arrayBuffer();
  await database.doc(`accounts/${owner.localId}`).update({state: 'DELETING', generation: 2, generationText: '2'});
  const deleted = await fetch(url, {headers: {Authorization: `Firebase ${owner.idToken}`, Range: `bytes=${offset}-`}});
  assert.ok([401, 403].includes(deleted.status));
  await deleted.arrayBuffer();
});

test('retiring an obsolete paid edit fences delayed delivery without blocking ordinary data or deletion', async () => {
  const uid = await account();
  const paid = {...operation('paid-obsolete', 'meal-1'), kind: 'nutrition_log'};
  assert.equal((await sync.retirePendingOperation(uid, paid)).status, 'CANCELED');
  assert.equal((await sync.push(uid, paid)).status, 'CANCELED');
  await assert.rejects(sync.push(uid, {...paid, payload: {...paid.payload, notes: 'reused'}}), /reused/);
  assert.equal((await sync.push(uid, operation('free-unblocked', 'ordinary-log'))).status, 'APPLIED');
  const removed = {...paid, operationId: 'paid-delete', deleted: true, payload: null};
  assert.equal((await sync.push(uid, removed)).status, 'APPLIED');
  assert.equal((await database.doc(`accounts/${uid}/entities/nutrition_log.${digest('meal-1')}`).get()).data().deleted, true);
});

test('retired large payloads do not need to upload bytes before replaying their cancellation', async () => {
  const uid = await account();
  const missing = {...operation('large-retired', 'large-meal'), kind: 'nutrition_log', payload: null,
    payloadRef: {sha256: digest('never-uploaded'), bytes: 600000}};
  assert.equal((await sync.retirePendingOperation(uid, missing)).status, 'CANCELED');
  assert.equal((await new SyncService(database).push(uid, missing)).status, 'CANCELED');
  assert.equal((await database.collection(`accounts/${uid}/entities`).get()).size, 0);
  await database.doc(`accounts/${uid}`).update({state: 'DELETING', generation: 2});
  await assert.rejects(sync.push(uid, missing), /unavailable/);
  await assert.rejects(sync.retirePendingOperation(uid, missing), /unavailable/);
});

test('an arrival racing retirement commits exactly one durable outcome', async () => {
  const uid = await account();
  for (let i = 0; i < 6; i++) {
    const op = operation(`retire-race-${i}`, `retire-log-${i}`);
    const [arrival, retirement] = await Promise.all([sync.push(uid, op), sync.retirePendingOperation(uid, op)]);
    assert.deepEqual(arrival, retirement);
    assert.deepEqual(await sync.push(uid, op), arrival);
    const stored = await database.doc(`accounts/${uid}/entities/workout_log.${digest(op.entityId)}`).get();
    assert.equal(stored.exists, arrival.status === 'APPLIED');
  }
});

test('persistent retries, disconnected conflicts, restart and deletion tombstones preserve data', async () => {
  const uid = await account();
  const op = operation('operation-1');
  const first = await sync.push(uid, op);
  assert.equal(first.status, 'APPLIED');
  assert.deepEqual(await new SyncService(database).push(uid, op), first);
  assert.equal((await database.collection(`accounts/${uid}/entities`).get()).size, 1);
  const edit = {...operation('operation-2'), payload: {...op.payload, weightKg: 75}};
  const conflict = await sync.push(uid, edit);
  assert.equal(conflict.status, 'CONFLICT');
  assert.equal((await sync.conflictPage(uid, 1))[0].incoming.payload.weightKg, 75);
  const changes = await sync.changes(uid, 1);
  assert.equal(changes.records[0].payload.weightKg, 70);
  assert.equal(changes.records[0].zoneId, 'America/Santiago');
  assert.equal(changes.cursor, 2); // Cursor also crosses a preserved conflict with no overwritten entity.
  assert.equal((await sync.changes(uid, 1, changes.cursor)).records.length, 0);
});

test('rules prevent cross-account reads, direct client writes and access to purchase tokens', async () => {
  const uidA = await account(), uidB = await account();
  await sync.push(uidA, operation('rule-op'));
  const entity = (await database.collection(`accounts/${uidA}/entities`).get()).docs[0];
  const clientA = rules.authenticatedContext(uidA).firestore();
  const clientB = rules.authenticatedContext(uidB).firestore();
  await assertSucceeds(getDoc(doc(clientA, entity.ref.path)));
  await assertFails(getDoc(doc(clientB, entity.ref.path)));
  await assertFails(getDoc(doc(rules.unauthenticatedContext().firestore(), entity.ref.path)));
  await assertFails(setDoc(doc(clientA, entity.ref.path), {revision: 99}));
  await database.doc('purchaseBindings/fixture-private').set({ownerUid: uidA, token: 'synthetic-test-token'});
  await assertFails(getDoc(doc(clientA, 'purchaseBindings/fixture-private')));
});

test('Free private data blobs exceed Firestore limits without exposing content or accepting corruption', async () => {
  const uid = await account(), other = await account();
  const payload = {id: 'large-log', notes: 'é'.repeat(1_500_000), repetitions: 8};
  const bytes = Buffer.from(JSON.stringify(payload)); const sha256 = digest(bytes);
  const path = `accounts/${uid}/1/data/${sha256}`;
  const storage = rules.authenticatedContext(uid).storage(bucketName);
  await assertSucceeds(uploadBytes(ref(storage, path), bytes, {contentType: 'application/json', customMetadata: {ownerUid: uid, fileId: sha256}}));
  await assertFails(getBytes(ref(rules.authenticatedContext(other).storage(bucketName), path)));
  const op = {...operation('large-op', payload.id), payload: null, payloadRef: {sha256, bytes: bytes.length}};
  const result = await sync.push(uid, op);
  assert.equal(result.status, 'APPLIED');
  assert.deepEqual(await new SyncService(database, () => Date.now(), bucket).push(uid, op), result);
  const stored = (await database.collection(`accounts/${uid}/entities`).get()).docs[0].data();
  assert.equal(stored.payloadJson, null); assert.equal(stored.payloadRef.sha256, sha256);
  assert.ok(JSON.stringify(stored).length < 1024);
  const page = await sync.changes(uid, 1); assert.equal(page.records[0].payloadRef.sha256, sha256);
  await assert.rejects(sync.push(uid, {...op, operationId: 'bad-size', payloadRef: {...op.payloadRef, bytes: bytes.length - 1}}), /size or type/);
  await assertFails(uploadBytes(ref(storage, path), Buffer.from('changed'), {contentType: 'application/json', customMetadata: {ownerUid: uid, fileId: sha256}}));
});

test('conflict choices are explicit, survive lost replies, and reject a changed reviewed revision', async () => {
  const uid = await account();
  await sync.push(uid, operation('first'));
  const incoming = {...operation('incompatible'), payload: {id: 'log-1', repetitions: 10}};
  const conflict = await sync.push(uid, incoming);
  await sync.push(uid, {...operation('newer', 'log-1', 1), payload: {id: 'log-1', repetitions: 12}});
  await assert.rejects(sync.resolveConflict(uid, 1, conflict.conflictId, null, 'KEEP_CURRENT', 1), /changed/);
  const resolved = await sync.resolveConflict(uid, 1, conflict.conflictId, null, 'KEEP_CURRENT', 2);
  assert.deepEqual(await sync.resolveConflict(uid, 1, conflict.conflictId, null, 'KEEP_CURRENT', 2), resolved);
  await assert.rejects(sync.resolveConflict(uid, 1, conflict.conflictId, null, 'USE_INCOMING', 2), /another choice/);
  assert.equal((await sync.changes(uid, 1)).records.at(-1).payload.repetitions, 12);
});

test('simultaneous conflict choices commit one reviewed choice together with its entity', async () => {
  const uid = await account();
  for (let attempt = 0; attempt < 6; attempt++) {
    const id = `race-${attempt}`;
    await sync.push(uid, operation(`first-${attempt}`, id));
    const incoming = {...operation(`edit-${attempt}`, id), payload: {id, weightKg: 90}};
    const conflict = await sync.push(uid, incoming);
    const choice = {...incoming, operationId: `resolution-${attempt}`, baseRevision: 1};
    const results = await Promise.allSettled([
      sync.resolveConflict(uid, 1, conflict.conflictId, choice, 'USE_INCOMING', 1),
      sync.resolveConflict(uid, 1, conflict.conflictId, null, 'KEEP_CURRENT', 1)]);
    assert.equal(results.filter(row => row.status === 'fulfilled').length, 1);
    const receipt = (await database.doc(`accounts/${uid}/conflicts/${conflict.conflictId}`).get()).data();
    const entity = (await database.doc(`accounts/${uid}/entities/workout_log.${digest(id)}`).get()).data();
    assert.equal(JSON.parse(entity.payloadJson).weightKg, receipt.resolution === 'KEEP_CURRENT' ? 70 : 90);
    assert.equal(entity.revision, receipt.resolution === 'KEEP_CURRENT' ? 1 : 2);
  }
});

test('keeping both assigns a distinct record identity without replacing the original', async () => {
  const uid = await account();
  await sync.push(uid, operation('duplicate-first'));
  const incoming = {...operation('duplicate-edit'), payload: {id: 'log-1', weightKg: 85}};
  const conflict = await sync.push(uid, incoming);
  const duplicate = {...incoming, operationId: 'duplicate-resolution', entityId: 'log-copy', sourceId: 'log-copy', payload: {...incoming.payload, id: 'log-copy'}};
  const result = await sync.resolveConflict(uid, 1, conflict.conflictId, duplicate, 'KEEP_BOTH', 1);
  assert.equal(result.status, 'APPLIED');
  assert.deepEqual(await sync.resolveConflict(uid, 1, conflict.conflictId, duplicate, 'KEEP_BOTH', 1), result);
  const rows = (await sync.changes(uid, 1)).records;
  assert.equal(rows.length, 2);
  assert.equal(rows[0].payload.id, 'log-1'); assert.equal(rows[0].payload.weightKg, 70);
  assert.equal(rows[1].payload.id, 'log-copy'); assert.equal(rows[1].payload.weightKg, 85);
});

test('guest incorporation is restart-safe, preserves legacy Pro records, and cannot cross accounts', async () => {
  const uid = await account(), other = await account();
  const op = {...operation('migration-op', 'legacy-meal'), kind: 'nutrition_log', guestImportId: 'migration-1',
    payload: {id: 'legacy-meal', grams: 150}};
  const manifestHash = digest(canonicalJson([{kind: op.kind, entityId: op.entityId, payloadHash: digest(canonicalJson(op.payload))}]));
  const grant = {guestProfileId: randomUUID(), deviceId: 'device-a', importId: 'migration-1', manifestHash, expectedRecords: 1};
  await sync.beginGuestImport(uid, grant);
  const result = await sync.push(uid, op);
  assert.equal(result.status, 'APPLIED');
  await sync.finishGuestImport(uid, grant.importId);
  assert.deepEqual(await new SyncService(database).push(uid, op), result);
  await assert.rejects(sync.beginGuestImport(other, grant), /another account/);
  const {guestImportId, ...normalEdit} = op;
  assert.equal((await sync.push(uid, {...normalEdit, operationId: 'same-content', baseRevision: 1})).revision, 1);
  await assert.rejects(sync.push(uid, {...normalEdit, operationId: 'new-edit', baseRevision: 1, payload: {...normalEdit.payload, grams: 180}}), /Pro/);
});

test('active session ownership and paginated changes survive a service restart', async () => {
  const uid = await account();
  const initial = {...operation('session-1', 'active'), kind: 'ongoing_workout'};
  await sync.push(uid, initial);
  const otherDevice = {...initial, operationId: 'session-2', deviceId: 'device-b', baseRevision: 1};
  assert.equal((await new SyncService(database).push(uid, otherDevice)).reason, 'ACTIVE_WORKOUT_OWNED_BY_ANOTHER_DEVICE');
  await sync.push(uid, operation('other-1', 'other-log'));
  const page = await sync.changes(uid, 1, 0, 1);
  assert.equal(page.records.length, 1); assert.equal(page.hasMore, true);
  const second = await sync.changes(uid, 1, page.cursor, 1);
  assert.equal(second.records[0].entityId, 'other-log');
});

test('an expired heartbeat never silently hands an ongoing session to another device', async () => {
  const uid = await account();
  const initial = {...operation('persistent-owner', 'owned-session'), kind: 'ongoing_workout'};
  await sync.push(uid, initial);
  const later = new SyncService(database, () => Date.now() + 10 * 60_000);
  const second = {...initial, operationId: 'second-device', baseRevision: 1, deviceId: 'device-b'};
  assert.equal((await later.push(uid, second)).status, 'CONFLICT');
  const cancel = {generation: 1, entityId: initial.entityId, expectedRevision: 1, deviceId: 'device-b', operationId: 'explicit-cancel', confirmation: 'CANCEL_REMOTE_WORKOUT'};
  assert.equal((await later.cancelRemoteWorkout(uid, cancel)).status, 'CANCELED');
  assert.deepEqual(await later.cancelRemoteWorkout(uid, cancel), {status: 'CANCELED', revision: 2});
  assert.equal((await sync.push(uid, {...initial, operationId: 'stale-owner-edit', baseRevision: 1})).status, 'CONFLICT');
  assert.equal((await later.push(uid, {...second, operationId: 'new-owned-session', baseRevision: 2})).status, 'APPLIED');
  const archived = (await database.doc(`accounts/${uid}/conflicts/explicit-cancel`).get()).data();
  assert.equal(JSON.parse(archived.archivedJson).payloadJson, canonicalJson(initial.payload));
});

test('Storage restricts media to its owner and confirmed Pro; expiry retains read and delete access', async () => {
  const uid = await account(), other = await account();
  const storage = rules.authenticatedContext(uid).storage(bucketName);
  const path = `accounts/${uid}/1/media/photo-1`;
  const metadata = {contentType: 'image/jpeg', customMetadata: {ownerUid: uid, fileId: 'photo-1'}};
  await assertFails(uploadBytes(ref(storage, path), new Uint8Array([1, 2, 3]), metadata));
  await database.doc(`entitlements/${uid}`).set({level: 'PRO', state: 'ACTIVE', validUntil: Timestamp.fromMillis(Date.now() + 60_000)});
  await assertSucceeds(uploadBytes(ref(storage, path), new Uint8Array([1, 2, 3]), metadata));
  await assertFails(getBytes(ref(rules.authenticatedContext(other).storage(bucketName), path)));
  await database.doc(`entitlements/${uid}`).update({level: 'FREE', state: 'EXPIRED'});
  assert.equal((await assertSucceeds(getBytes(ref(storage, path)))).byteLength, 3);
  await assertFails(uploadBytes(ref(storage, path), new Uint8Array([4]), metadata));
});

test('verified Play state, acknowledgement, repeated RTDN, pending and account binding use persistent receipts', async () => {
  const uid = await account(), other = await account();
  let state = 'SUBSCRIPTION_STATE_ACTIVE', acknowledgements = 0;
  const play = {get: async () => ({externalAccountIdentifiers: {obfuscatedExternalAccountId: digest(uid)},
    subscriptionState: state, lineItems: [{productId: 'kpkn_pro', expiryTime: new Date(Date.now() + 60_000).toISOString()}],
    acknowledgementState: acknowledgements ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : 'ACKNOWLEDGEMENT_STATE_PENDING'}),
    acknowledge: async () => { acknowledgements++; }};
  const billing = new BillingService(database, play);
  assert.equal((await billing.verify(uid, 'synthetic-purchase')).level, 'PRO');
  assert.equal(acknowledgements, 1);
  assert.equal((await billing.verify(uid, 'synthetic-purchase')).level, 'PRO');
  await assert.rejects(billing.verify(other, 'synthetic-purchase'), /different|another/);
  state = 'SUBSCRIPTION_STATE_ON_HOLD';
  await billing.handleNotification({eventId: 'rtdn-1', packageName: 'com.example.kpkn', expectedPackage: 'com.example.kpkn', purchaseToken: 'synthetic-purchase'});
  assert.equal((await database.doc(`entitlements/${uid}`).get()).data().level, 'FREE');
  await billing.handleNotification({eventId: 'rtdn-1', packageName: 'com.example.kpkn', expectedPackage: 'com.example.kpkn', purchaseToken: 'synthetic-purchase'});
  assert.equal((await database.collection('billingEvents').get()).size, 1);
});

test('interrupted account deletion blocks old devices immediately and resumes without data revival', async () => {
  const uid = await account(); await sync.push(uid, operation('before-delete'));
  let cancelFails = true;
  await database.doc(`purchaseBindings/${digest('deletion-token')}`).set({ownerUid: uid, ownerHash: digest(uid), token: 'deletion-token', state: 'ACTIVE'});
  await database.doc('billingEvents/deleted-owner-event').set({purchaseHash: digest('deletion-token')});
  await database.doc('guestBindings/deleted-owner-guest').set({ownerHash: digest(uid), importId: 'personal-import-id'});
  const deleting = new DeletionService(database, auth, bucket, {cancel: async () => { if (cancelFails) throw Error('simulated outage'); }});
  assert.equal((await deleting.request(uid, Math.floor(Date.now() / 1000), 'DELETE_ACCOUNT')).status, 'DELETION_PENDING');
  assert.equal((await database.doc(`accounts/${uid}`).get()).data().state, 'DELETING');
  await assert.rejects(sync.push(uid, operation('after-delete')), /unavailable|generation/);
  const client = rules.authenticatedContext(uid).firestore();
  const entity = (await database.collection(`accounts/${uid}/entities`).get()).docs[0];
  await assertFails(getDoc(doc(client, entity.ref.path)));
  cancelFails = false; await deleting.resume(uid);
  assert.equal((await database.doc(`accounts/${uid}`).get()).data().state, 'DELETED');
  assert.equal((await database.collection(`accounts/${uid}/entities`).get()).size, 0);
  await assert.rejects(sync.openAccount(uid), /deleted/);
  const binding = (await database.doc(`purchaseBindings/${digest('deletion-token')}`).get()).data();
  assert.equal(binding.token, undefined); assert.equal(binding.ownerUid, undefined);
  assert.equal((await database.doc('billingEvents/deleted-owner-event').get()).exists, false);
  assert.equal((await database.doc('guestBindings/deleted-owner-guest').get()).data().importId, undefined);
  await assert.rejects(auth.getUser(uid), /no user record/);
});

test('media references are accepted only after verifying private bytes and their checksum', async () => {
  const uid = await account();
  await database.doc(`entitlements/${uid}`).set({level: 'PRO', state: 'ACTIVE', validUntilEpochMs: Date.now() + 60000, validUntil: Timestamp.fromMillis(Date.now() + 60000)});
  const bytes = Buffer.from('synthetic-media-bytes'), hash = digest(bytes);
  const reference = {role: 'main', sha256: hash, bytes: bytes.length, contentType: 'image/jpeg'};
  const op = {...operation('verified-media', 'media-record'), kind: 'workout_media', payload: {id: 'media-record', kind: 'PHOTO', mediaFiles: [reference]}};
  await assert.rejects(sync.push(uid, op)); // A record never confirms a file which has not arrived.
  const file = bucket.file(`accounts/${uid}/1/media/${hash}`);
  await file.save(bytes, {metadata: {contentType: 'image/jpeg', metadata: {ownerUid: uid, fileId: hash}}});
  assert.equal((await sync.push(uid, op)).status, 'APPLIED');
  await assert.rejects(sync.push(uid, {...op, operationId: 'bad-media-size', payload: {...op.payload, mediaFiles: [{...reference, bytes: bytes.length + 1}]}}), /ownership changed/);
  await file.save(Buffer.from('incorrect-bytes-length'), {metadata: {contentType: 'image/jpeg', metadata: {ownerUid: uid, fileId: hash}}});
  const wrong = {...reference, bytes: Buffer.byteLength('incorrect-bytes-length')};
  await assert.rejects(sync.push(uid, {...op, operationId: 'bad-media-hash', payload: {...op.payload, mediaFiles: [wrong]}}), /checksum/);
});

test('late uploads cannot leave files behind after account deletion', async () => {
  const uid = await account();
  const guard = new AccountObjectGuard(database, getStorage(app));
  const file = bucket.file(`accounts/${uid}/1/media/late-file`);
  await file.save(Buffer.from('synthetic image'), {metadata: {contentType: 'image/jpeg'}});
  const [metadata] = await file.getMetadata();
  assert.equal((await guard.finalized(metadata)).status, 'OWNED');
  await database.doc(`accounts/${uid}`).update({state: 'DELETED'});
  assert.equal((await guard.finalized(metadata)).status, 'PURGED');
  assert.equal((await file.exists())[0], false);
  assert.equal((await guard.finalized(metadata)).status, 'ALREADY_REMOVED_OR_REPLACED');
});

test('an expired or revoked old token never removes newer confirmed Pro access', async () => {
  const uid = await account();
  let oldMissing = false;
  const play = {get: async token => {
    if (token === 'old-token' && oldMissing) { const error = Error('absent'); error.code = 'not-found'; throw error; }
    return {externalAccountIdentifiers: {obfuscatedExternalAccountId: digest(uid)},
      subscriptionState: token === 'new-token' ? 'SUBSCRIPTION_STATE_ACTIVE' : 'SUBSCRIPTION_STATE_EXPIRED',
      lineItems: [{productId: 'kpkn_pro', expiryTime: new Date(Date.now() + (token === 'new-token' ? 3_600_000 : -60_000)).toISOString()}],
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'};
  }, acknowledge: async () => assert.fail('Already acknowledged')};
  const billing = new BillingService(database, play);
  assert.equal((await billing.verify(uid, 'old-token')).level, 'FREE');
  assert.equal((await billing.verify(uid, 'new-token')).level, 'PRO');
  assert.equal((await billing.verify(uid, 'old-token')).level, 'PRO');
  oldMissing = true;
  assert.equal((await billing.verify(uid, 'old-token')).level, 'PRO');
  const confirmed = (await database.doc(`entitlements/${uid}`).get()).data();
  assert.equal(confirmed.purchaseHash, digest('new-token'));
});

test('pending purchases never grant Pro, and unused Auth accounts are deletable', async () => {
  const uid = await account();
  const billing = new BillingService(database, {get: async () => ({externalAccountIdentifiers: {obfuscatedExternalAccountId: digest(uid)},
    subscriptionState: 'SUBSCRIPTION_STATE_PENDING', lineItems: [{productId: 'kpkn_pro'}]}), acknowledge: async () => assert.fail('Pending acknowledged')});
  assert.equal((await billing.verify(uid, 'pending-token')).level, 'FREE');
  const unused = randomUUID(); await auth.createUser({uid: unused});
  const deleting = new DeletionService(database, auth, bucket, {cancel: async () => {}});
  assert.equal((await deleting.request(unused, Math.floor(Date.now() / 1000), 'DELETE_ACCOUNT')).status, 'DELETED');
  await assert.rejects(auth.getUser(unused), /no user record/);
  await assert.rejects(deleting.request(uid, Math.floor(Date.now() / 1000) - 3600, 'DELETE_ACCOUNT'), /Recent/);
});

test('callable transport verifies Firebase Auth instead of accepting a caller-supplied UID', async () => {
  const signup = await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-only',
    {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({email: `${randomUUID()}@example.test`, password: 'EmulatorOnlyPass123!', returnSecureToken: true})});
  const identity = await signup.json(); assert.ok(identity.idToken);
  const url = `http://127.0.0.1:15001/${projectId}/us-central1/openAccount`;
  const untrusted = await fetch(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({data: {uid: 'victim'}})});
  assert.equal(untrusted.status, 401);
  const trusted = await fetch(url, {method: 'POST', headers: {'Content-Type': 'application/json', Authorization: `Bearer ${identity.idToken}`}, body: JSON.stringify({data: {uid: 'victim'}})});
  const response = await trusted.json(); assert.equal(response.result.generation, 1);
  assert.equal((await database.doc(`accounts/${identity.localId}`).get()).data().state, 'ACTIVE');
  assert.equal((await database.doc('accounts/victim').get()).exists, false);
});
