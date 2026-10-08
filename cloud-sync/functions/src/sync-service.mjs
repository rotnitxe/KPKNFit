import {FieldValue} from 'firebase-admin/firestore';
import {canonicalJson, decide, digest, fail, validateOperation, MAX_DATA_BLOB_BYTES} from './protocol.mjs';

const identifier = /^[A-Za-z0-9_.:-]{1,128}$/;
const entityKey = operation => `${operation.kind}.${digest(operation.entityId)}`;
const decode = document => {
  if (!document) return null;
  const {payloadJson, ...metadata} = document;
  return {...metadata, payload: payloadJson == null ? null : JSON.parse(payloadJson)};
};
const encode = document => {
  const {payload, ...metadata} = document;
  return {...metadata, payloadJson: payload == null || document.payloadRef ? null : canonicalJson(payload)};
};

/** Each operation and its receipt/change cursor commit together. No client writes bypass this service. */
export class SyncService {
  constructor(database, clock = () => Date.now(), bucket = null) { this.db = database; this.clock = clock; this.bucket = bucket; }
  async materialize(uid, wire) {
    if (!wire.payloadRef) return wire;
    if (!this.bucket) fail('unavailable', 'Private data transport is unavailable');
    const account = (await this.accountRef(uid).get()).data();
    if (account?.state !== 'ACTIVE' || account.generation !== wire.accountGeneration) fail('permission-denied', 'Account is unavailable');
    const file = this.bucket.file(`accounts/${uid}/${wire.accountGeneration}/data/${wire.payloadRef.sha256}`);
    const [metadata] = await file.getMetadata();
    if (+metadata.size !== wire.payloadRef.bytes || +metadata.size > MAX_DATA_BLOB_BYTES || metadata.contentType !== 'application/json' ||
        metadata.metadata?.ownerUid !== uid || metadata.metadata?.fileId !== wire.payloadRef.sha256) fail('invalid-argument', 'Private data size or type changed');
    const chunks = []; let size = 0;
    for await (const chunk of file.createReadStream()) {
      size += chunk.length;
      if (size > wire.payloadRef.bytes) fail('invalid-argument', 'Private data exceeds its declared size');
      chunks.push(chunk);
    }
    const bytes = Buffer.concat(chunks);
    if (size !== wire.payloadRef.bytes || digest(bytes) !== wire.payloadRef.sha256) fail('invalid-argument', 'Private data verification failed');
    let payload;
    try { payload = JSON.parse(bytes.toString('utf8')); } catch { fail('invalid-argument', 'Invalid private data'); }
    const result = {...wire, payload};
    validateOperation(result, {materialized: true});
    return result;
  }
  accountRef(uid) { return this.db.doc(`accounts/${uid}`); }
  /** Retiring an obsolete in-flight edit fences delayed delivery atomically with its receipt. */
  async retirePendingOperation(uid, operation) {
    validateOperation(operation);
    if (operation.guestImportId) fail('invalid-argument', 'Guest snapshots cannot be retired');
    const ref = this.accountRef(uid).collection('operations').doc(operation.operationId);
    const wireFingerprint = digest(canonicalJson(operation));
    const previous = (await ref.get()).data();
    const legacyFingerprint = previous && !previous.wireFingerprint ?
      digest(canonicalJson(await this.materialize(uid, operation))) : null;
    return this.db.runTransaction(async transaction => {
      const [account, receipt] = await transaction.getAll(this.accountRef(uid), ref);
      if (account.data()?.state !== 'ACTIVE' || account.data().generation !== operation.accountGeneration) fail('permission-denied', 'Account is unavailable');
      if (receipt.exists) {
        const saved = receipt.data();
        if ((saved.wireFingerprint ?? saved.fingerprint) !== (saved.wireFingerprint ? wireFingerprint : legacyFingerprint ?? wireFingerprint)) fail('already-exists', 'Operation ID was reused with different data');
        return saved.result;
      }
      const window = Math.floor(this.clock() / 60_000);
      const count = account.data().quotaWindow === window ? account.data().quotaCount : 0;
      if (count >= 120) fail('resource-exhausted', 'Synchronization rate limit reached');
      const result = {status: 'CANCELED', operationId: operation.operationId};
      transaction.create(ref, {fingerprint: wireFingerprint, wireFingerprint, retired: true, result, createdAtEpochMs: this.clock()});
      transaction.update(this.accountRef(uid), {quotaWindow: window, quotaCount: count + 1});
      return result;
    });
  }
  async verifyMedia(uid, operation) {
    if (operation.kind !== 'workout_media' || operation.deleted || !operation.payload?.mediaFiles) return;
    const files = operation.payload.mediaFiles;
    if (!Array.isArray(files) || files.length > 3 || new Set(files.map(file => file.role)).size !== files.length) fail('invalid-argument', 'Invalid media references');
    for (const reference of files) {
      if (!['main', 'thumbnail', 'pose'].includes(reference.role) || !/^[a-f0-9]{64}$/.test(reference.sha256 ?? '') ||
          !Number.isSafeInteger(reference.bytes) || reference.bytes < 1 || reference.bytes > 256 * 1024 * 1024 ||
          !['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'image/avif', 'video/mp4', 'application/json'].includes(reference.contentType) ||
          (reference.contentType === 'application/json' && reference.role !== 'pose')) fail('invalid-argument', 'Invalid media reference');
      if (!this.bucket) fail('unavailable', 'Media storage is unavailable');
      const file = this.bucket.file(`accounts/${uid}/${operation.accountGeneration}/media/${reference.sha256}`);
      const [metadata] = await file.getMetadata();
      if (+metadata.size !== reference.bytes || metadata.contentType !== reference.contentType || metadata.metadata?.ownerUid !== uid ||
          metadata.metadata?.fileId !== reference.sha256) fail('invalid-argument', 'Media size or ownership changed');
      const hasher = (await import('node:crypto')).createHash('sha256');
      let size = 0;
      for await (const chunk of file.createReadStream()) {
        size += chunk.length;
        if (size > reference.bytes) fail('invalid-argument', 'Media exceeds its declared size');
        hasher.update(chunk);
      }
      if (size !== reference.bytes || hasher.digest('hex') !== reference.sha256) fail('invalid-argument', 'Media checksum mismatch');
    }
  }
  async openAccount(uid) {
    return this.db.runTransaction(async transaction => {
      const ref = this.accountRef(uid);
      const current = (await transaction.get(ref)).data();
      if (current && current.state !== 'ACTIVE') fail('permission-denied', 'This account has been deleted');
      if (current) return {schemaVersion: 1, generation: current.generation, sequence: current.sequence};
      transaction.create(ref, {state: 'ACTIVE', generation: 1, generationText: '1', sequence: 0,
        createdAtEpochMs: this.clock(), quotaWindow: 0, quotaCount: 0});
      return {schemaVersion: 1, generation: 1, sequence: 0};
    });
  }

  async push(uid, operation, {approvedLegacyResolution = false, resolution = null} = {}) {
    validateOperation(operation);
    const wire = operation;
    const wireFingerprint = digest(canonicalJson(wire));
    // A replay or canceled upload does not read or re-upload media. Account revocation still wins.
    if (!resolution) {
      const saved = await this.db.runTransaction(async transaction => {
        const [account, receipt] = await transaction.getAll(this.accountRef(uid),
          this.accountRef(uid).collection('operations').doc(operation.operationId));
        if (account.data()?.state !== 'ACTIVE' || account.data().generation !== operation.accountGeneration) fail('permission-denied', 'Account is unavailable');
        if (!receipt.data()?.wireFingerprint) return null;
        if (receipt.data().wireFingerprint !== wireFingerprint) fail('already-exists', 'Operation ID was reused with different data');
        return receipt.data().result;
      });
      if (saved) return saved;
    }
    operation = await this.materialize(uid, wire);
    await this.verifyMedia(uid, operation);
    if (operation.guestImportId && (!identifier.test(operation.guestImportId) || operation.baseRevision !== 0 || operation.deleted)) {
      fail('invalid-argument', 'Guest import can only incorporate original records');
    }
    const accountRef = this.accountRef(uid);
    const entityRef = accountRef.collection('entities').doc(entityKey(operation));
    const receiptRef = accountRef.collection('operations').doc(operation.operationId);
    const leaseRef = accountRef.collection('leases').doc('active-workout');
    const now = this.clock();
    return this.db.runTransaction(async transaction => {
      const [accountSnapshot, entitySnapshot, receiptSnapshot, leaseSnapshot, entitlementSnapshot] =
        await transaction.getAll(accountRef, entityRef, receiptRef, leaseRef, this.db.doc(`entitlements/${uid}`));
      const account = accountSnapshot.data();
      if (account?.state !== 'ACTIVE' || account.generation !== operation.accountGeneration) fail('permission-denied', 'Account is unavailable');
      if (receiptSnapshot.data()?.retired) {
        if (receiptSnapshot.data().wireFingerprint !== wireFingerprint) fail('already-exists', 'Operation ID was reused with different data');
        return receiptSnapshot.data().result;
      }
      // The reviewed choice and its resulting entity write must commit in the SAME transaction.
      // Otherwise another device can keep the current version between a push and the resolution receipt.
      if (resolution) {
        const saved = (await transaction.get(resolution.ref)).data();
        if (!saved) fail('not-found', 'Conflict is unavailable');
        if (saved.state === 'RESOLVED') {
          if (saved.resolution !== resolution.choice) fail('already-exists', 'Conflict was resolved with another choice');
          return saved.result;
        }
        const origin = resolution.originKey === entityRef.id ? entitySnapshot :
          await transaction.get(accountRef.collection('entities').doc(resolution.originKey));
        if ((origin.data()?.revision ?? 0) !== resolution.expectedRevision) fail('failed-precondition', 'Current version changed; review the conflict again');
      }
      let approvedGuestImport = approvedLegacyResolution;
      let importRef, manifestRef, newManifestEntry;
      if (operation.guestImportId) {
        importRef = accountRef.collection('guestImports').doc(operation.guestImportId);
        const grant = (await transaction.get(importRef)).data();
        approvedGuestImport = grant?.deviceId === operation.deviceId &&
          ((grant.state === 'OPEN' && grant.deadlineEpochMs > now) || receiptSnapshot.exists);
        if (!approvedGuestImport) fail('failed-precondition', 'Guest import is unavailable');
        manifestRef = importRef.collection('entries').doc(entityKey(operation));
        const existingEntry = (await transaction.get(manifestRef)).data();
        const entry = {kind: operation.kind, entityId: operation.entityId, payloadHash: digest(canonicalJson(operation.payload))};
        if (existingEntry && existingEntry.payloadHash !== entry.payloadHash) fail('already-exists', 'Guest snapshot content changed');
        if (!existingEntry) {
          if ((grant.receivedCount ?? 0) >= grant.expectedRecords) fail('invalid-argument', 'Guest snapshot has additional records');
          newManifestEntry = entry;
        }
      }
      const decision = decide({uid, account, entitlement: entitlementSnapshot.data(), operation,
        current: decode(entitySnapshot.data()), receipt: receiptSnapshot.data(), lease: leaseSnapshot.data(),
        now, approvedGuestImport});
      if (decision.replay) return decision.receipt.result;
      const window = Math.floor(now / 60_000);
      const count = account.quotaWindow === window ? account.quotaCount : 0;
      if (count >= 120) fail('resource-exhausted', 'Synchronization rate limit reached');
      const nextSequence = (account.sequence ?? 0) + 1;
      let result;
      if (decision.conflict) {
        transaction.create(accountRef.collection('conflicts').doc(operation.operationId), {
          incomingJson: canonicalJson(wire), reason: decision.reason, currentRevision: entitySnapshot.data()?.revision ?? 0,
          createdAtEpochMs: now, sequence: nextSequence, state: 'UNRESOLVED'});
        result = {status: 'CONFLICT', operationId: operation.operationId, conflictId: operation.operationId,
          reason: decision.reason, currentRevision: entitySnapshot.data()?.revision ?? 0, sequence: nextSequence};
      } else {
        const document = encode(decision.document);
        transaction.set(entityRef, document);
        transaction.create(accountRef.collection('changes').doc(String(nextSequence).padStart(20, '0')),
          {...document, sequence: nextSequence});
        if (operation.kind === 'ongoing_workout') {
          if (operation.deleted) {
            if (!leaseSnapshot.exists || leaseSnapshot.data().deviceId === operation.deviceId) transaction.delete(leaseRef);
          } else transaction.set(leaseRef, {deviceId: operation.deviceId, entityId: operation.entityId,
            expiresAtEpochMs: now + 5 * 60_000});
        }
        result = {status: 'APPLIED', operationId: operation.operationId, revision: decision.document.revision, sequence: nextSequence};
      }
      transaction.create(receiptRef, {fingerprint: decision.fingerprint, wireFingerprint, result, createdAtEpochMs: now});
      if (resolution && result.status === 'APPLIED') transaction.update(resolution.ref, {
        state: 'RESOLVED', resolution: resolution.choice, result, resolvedAtEpochMs: now});
      transaction.update(accountRef, {sequence: nextSequence, quotaWindow: window, quotaCount: count + 1});
      if (newManifestEntry) {
        transaction.create(manifestRef, newManifestEntry);
        transaction.update(importRef, {receivedCount: FieldValue.increment(1)});
      }
      return result;
    });
  }

  async changes(uid, generation, after = 0, limit = 50) {
    if (!Number.isSafeInteger(generation) || !Number.isSafeInteger(after) || after < 0 ||
        !Number.isInteger(limit) || limit < 1 || limit > 50) fail('invalid-argument', 'Invalid change cursor');
    const ref = this.accountRef(uid);
    const account = (await ref.get()).data();
    if (account?.state !== 'ACTIVE' || account.generation !== generation) fail('permission-denied', 'Account is unavailable');
    const page = await ref.collection('changes').where('sequence', '>', after).where('sequence', '<=', account.sequence)
      .orderBy('sequence').limit(limit).get();
    // Recheck after reading, so an account deleted during pagination cannot keep supplying data.
    const fresh = (await ref.get()).data();
    if (fresh?.state !== 'ACTIVE' || fresh.generation !== generation) fail('permission-denied', 'Account is unavailable');
    const records = [];
    let bytes = 0;
    for (const row of page.docs) {
      const record = decode(row.data());
      const size = Buffer.byteLength(JSON.stringify(record));
      if (bytes + size > 4 * 1024 * 1024) break;
      records.push(record); bytes += size;
    }
    const hasMore = records.length < page.size || page.size === limit;
    return {schemaVersion: 1, generation, records, cursor: hasMore ? records.at(-1)?.sequence ?? after : account.sequence,
      hasMore, serverSequence: account.sequence};
  }

  async conflictPage(uid, generation) {
    const account = (await this.accountRef(uid).get()).data();
    if (account?.state !== 'ACTIVE' || account.generation !== generation) fail('permission-denied', 'Account is unavailable');
    const page = await this.accountRef(uid).collection('conflicts').where('state', '==', 'UNRESOLVED').limit(50).get();
    return page.docs.map(doc => {
      const {incomingJson, ...metadata} = doc.data();
      return {id: doc.id, ...metadata, incoming: JSON.parse(incomingJson)};
    });
  }

  async cancelRemoteWorkout(uid, {generation, entityId, expectedRevision, deviceId, operationId, confirmation}) {
    if (![entityId, deviceId, operationId].every(value => identifier.test(value ?? '')) ||
        !Number.isSafeInteger(expectedRevision) || confirmation !== 'CANCEL_REMOTE_WORKOUT') fail('invalid-argument', 'Explicit session cancellation is required');
    const ref = this.accountRef(uid);
    const currentRef = ref.collection('entities').doc(entityKey({kind: 'ongoing_workout', entityId}));
    const receiptRef = ref.collection('operations').doc(operationId);
    const fingerprint = digest(canonicalJson({generation, entityId, expectedRevision, deviceId, confirmation}));
    return this.db.runTransaction(async transaction => {
      const [account, current, receipt] = await transaction.getAll(ref, currentRef, receiptRef);
      if (account.data()?.state !== 'ACTIVE' || account.data().generation !== generation) fail('permission-denied', 'Account is unavailable');
      if (receipt.exists) {
        if (receipt.data().fingerprint !== fingerprint) fail('already-exists', 'Operation identity changed');
        return receipt.data().result;
      }
      const document = current.data();
      if (!document || document.revision !== expectedRevision) fail('failed-precondition', 'The active session changed; review it again');
      if (document.deleted) return {status: 'CANCELED', revision: document.revision};
      const sequence = account.data().sequence + 1;
      const tombstone = {...document, revision: document.revision + 1, deleted: true, payloadJson: null,
        deviceId, confirmedAtEpochMs: this.clock()};
      delete tombstone.payloadRef;
      transaction.create(ref.collection('conflicts').doc(operationId), {state: 'RESOLVED', resolution: 'EXPLICITLY_CANCELED_REMOTE_SESSION',
        archivedJson: canonicalJson(document), reason: 'USER_CANCELED_REMOTE_SESSION', createdAtEpochMs: this.clock(), sequence});
      transaction.set(currentRef, tombstone);
      transaction.create(ref.collection('changes').doc(String(sequence).padStart(20, '0')), {...tombstone, sequence});
      transaction.delete(ref.collection('leases').doc('active-workout'));
      transaction.update(ref, {sequence});
      const result = {status: 'CANCELED', revision: tombstone.revision};
      transaction.create(receiptRef, {fingerprint, result, createdAtEpochMs: this.clock()});
      return result;
    });
  }

  async resolveConflict(uid, generation, conflictId, operation, resolution, expectedRevision = null) {
    if (!identifier.test(conflictId) || !['KEEP_CURRENT', 'USE_INCOMING', 'KEEP_BOTH'].includes(resolution)) fail('invalid-argument', 'Invalid conflict resolution');
    const conflictRef = this.accountRef(uid).collection('conflicts').doc(conflictId);
    const conflict = (await conflictRef.get()).data();
    if (!conflict) fail('not-found', 'Conflict is unavailable');
    const account = (await this.accountRef(uid).get()).data();
    if (account?.state !== 'ACTIVE' || account.generation !== generation) fail('permission-denied', 'Account is unavailable');
    if (conflict.state === 'RESOLVED') {
      if (conflict.resolution !== resolution) fail('already-exists', 'Conflict was resolved with another choice');
      return conflict.result;
    }
    let result = {status: 'KEPT_CURRENT'};
    if (resolution !== 'KEEP_CURRENT') {
      const original = JSON.parse(conflict.incomingJson);
      const duplicable = new Set(['program', 'workout_log', 'competition', 'body_observation', 'body_goal',
        'custom_food', 'custom_exercise', 'session_template', 'meal_template']);
      if (!Number.isSafeInteger(expectedRevision) || expectedRevision < 0) fail('invalid-argument', 'A reviewed revision is required');
      let expectedPayload = original.payload;
      let candidatePayload = operation?.payload;
      if (resolution === 'KEEP_BOTH') {
        const preserved = await this.materialize(uid, original);
        if (!duplicable.has(original.kind) || original.deleted || !preserved.payload?.id ||
            !identifier.test(operation?.sourceId ?? '') || operation.sourceId === original.sourceId ||
            operation.entityId !== operation.sourceId) fail('invalid-argument', 'This record cannot be duplicated');
        validateOperation(operation);
        expectedPayload = {...preserved.payload, id: operation.sourceId};
        candidatePayload = (await this.materialize(uid, operation)).payload;
      }
      if (!operation || operation.kind !== original.kind || operation.accountGeneration !== generation ||
          operation.deleted !== original.deleted || operation.baseRevision !== (resolution === 'KEEP_BOTH' ? 0 : expectedRevision) ||
          canonicalJson(candidatePayload) !== canonicalJson(expectedPayload) ||
          (resolution === 'USE_INCOMING' && canonicalJson(operation.payloadRef ?? null) !== canonicalJson(original.payloadRef ?? null)) ||
          (resolution === 'USE_INCOMING' && operation.entityId !== original.entityId) ||
          (resolution === 'KEEP_BOTH' && operation.entityId === original.entityId)) fail('invalid-argument', 'Resolution does not match the preserved edit');
      let approvedLegacyResolution = false;
      if (original.guestImportId) {
        const grant = (await this.accountRef(uid).collection('guestImports').doc(original.guestImportId).get()).data();
        approvedLegacyResolution = grant?.state === 'COMPLETE';
      }
      return this.push(uid, operation, {approvedLegacyResolution, resolution: {ref: conflictRef,
        choice: resolution, expectedRevision, originKey: entityKey(original)}});
    }
    return this.db.runTransaction(async transaction => {
      const original = JSON.parse(conflict.incomingJson);
      const [freshAccount, freshConflict, entity] = await transaction.getAll(this.accountRef(uid), conflictRef,
        this.accountRef(uid).collection('entities').doc(entityKey(original)));
      if (freshAccount.data()?.state !== 'ACTIVE' || freshAccount.data().generation !== generation) fail('permission-denied', 'Account is unavailable');
      if (freshConflict.data()?.state === 'RESOLVED') {
        if (freshConflict.data().resolution !== resolution) fail('already-exists', 'Conflict was resolved with another choice');
        return freshConflict.data().result;
      }
      if (resolution === 'KEEP_CURRENT') {
        const revision = entity.data()?.revision ?? 0;
        if (expectedRevision != null && revision !== expectedRevision) fail('failed-precondition', 'Current version changed; review the conflict again');
        result = {status: 'KEPT_CURRENT', revision};
      }
      transaction.update(conflictRef, {state: 'RESOLVED', resolution, result, resolvedAtEpochMs: this.clock()});
      return result;
    });
  }

  async beginGuestImport(uid, {guestProfileId, deviceId, importId, manifestHash, expectedRecords}) {
    if (![guestProfileId, deviceId, importId].every(value => identifier.test(value ?? '')) || !/^[a-f0-9]{64}$/.test(manifestHash ?? '')) fail('invalid-argument', 'Invalid guest import identity');
    if (!Number.isInteger(expectedRecords) || expectedRecords < 0 || expectedRecords > 100_000) fail('invalid-argument', 'Invalid guest snapshot count');
    return this.db.runTransaction(async transaction => {
      const accountRef = this.accountRef(uid);
      const grantRef = accountRef.collection('guestImports').doc(importId);
      const bindingRef = this.db.doc(`guestBindings/${digest(guestProfileId)}`);
      const [account, grant, binding] = await transaction.getAll(accountRef, grantRef, bindingRef);
      if (account.data()?.state !== 'ACTIVE') fail('permission-denied', 'Account is unavailable');
      if (binding.exists && binding.data().ownerHash !== digest(uid)) fail('permission-denied', 'Guest data already belongs to another account');
      if (grant.exists && (grant.data().manifestHash !== manifestHash || grant.data().expectedRecords !== expectedRecords)) fail('already-exists', 'Import identity was reused');
      if (binding.exists && binding.data().importId !== importId) fail('already-exists', 'Guest profile was already imported');
      const deadlineEpochMs = this.clock() + 30 * 60_000;
      transaction.set(bindingRef, {ownerHash: digest(uid), importId});
      transaction.set(grantRef, {manifestHash, deviceId, guestProfileHash: digest(guestProfileId),
        expectedRecords, receivedCount: grant.data()?.receivedCount ?? 0,
        deadlineEpochMs, state: grant.data()?.state === 'COMPLETE' ? 'COMPLETE' : 'OPEN'});
      return {importId, generation: account.data().generation, deadlineEpochMs, state: grant.data()?.state ?? 'OPEN'};
    });
  }

  async finishGuestImport(uid, importId) {
    if (!identifier.test(importId ?? '')) fail('invalid-argument', 'Invalid import identity');
    const account = (await this.accountRef(uid).get()).data();
    if (account?.state !== 'ACTIVE') fail('permission-denied', 'Account is unavailable');
    const grant = this.accountRef(uid).collection('guestImports').doc(importId);
    const prepared = await this.db.runTransaction(async transaction => {
      const current = (await transaction.get(grant)).data();
      if (!current) fail('not-found', 'Guest import is unavailable');
      if (current.receivedCount !== current.expectedRecords) fail('failed-precondition', 'Guest snapshot is incomplete');
      if (current.state !== 'COMPLETE') transaction.update(grant, {state: 'VERIFYING'});
      return current;
    });
    const rows = await grant.collection('entries').get();
    const manifest = rows.docs.map(row => row.data()).sort((a, b) => `${a.kind}:${a.entityId}` < `${b.kind}:${b.entityId}` ? -1 : 1);
    if (manifest.length !== prepared.expectedRecords || digest(canonicalJson(manifest)) !== prepared.manifestHash) {
      await grant.update({state: 'INVALID'});
      fail('failed-precondition', 'Guest snapshot verification failed');
    }
    await this.db.runTransaction(async transaction => {
      const current = (await transaction.get(this.accountRef(uid))).data();
      if (current?.state !== 'ACTIVE' || current.generation !== account.generation) fail('permission-denied', 'Account is unavailable');
      transaction.update(grant, {state: 'COMPLETE', completedAtEpochMs: this.clock()});
    });
    return {status: 'COMPLETE', verifiedRecords: manifest.length};
  }
}
