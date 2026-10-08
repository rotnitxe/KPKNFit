import test from 'node:test';
import assert from 'node:assert/strict';
import {decide, validateOperation, validatePurchaseSnapshot, digest} from '../src/protocol.mjs';

const now = 1791400000000;
const operation = overrides => ({schemaVersion: 1, operationId: 'op-1', deviceId: 'device-a',
  accountGeneration: 1, kind: 'workout_log', entityId: 'log-1', baseRevision: 0,
  changedAtEpochMs: now - 1000, zoneId: 'America/Santiago', unitSystem: 'METRIC',
  deleted: false, payload: {id: 'log-1', date: '2026-10-07'}, ...overrides});
const context = overrides => ({uid: 'user-a', account: {state: 'ACTIVE', generation: 1}, operation: operation(), now, ...overrides});

test('retry is idempotent and operation IDs cannot be reused for other data', () => {
  const op = operation(); const result = decide(context());
  const receipt = {fingerprint: result.fingerprint, revision: 1};
  assert.equal(decide(context({receipt})).replay, true);
  assert.throws(() => decide(context({receipt, operation: operation({payload: {id: 'log-1', notes: 'changed'}})})), /reused/);
});
test('a source identity cannot confirm a different record identity', () => {
  assert.throws(() => validateOperation(operation({payload: {id: 'another-record'}})), /identity/);
  assert.throws(() => validateOperation(operation({sourceId: 'log-1', payload: {id: 'another-record'}})), /identity/);
  assert.equal(validateOperation(operation({sourceId: 'log-1'})).sourceId, 'log-1');
  assert.throws(() => validateOperation(operation({sourceId: 'log-1', entityId: 'unrelated'})), /identity/);
  const raw = 'exercise | location | equipment';
  assert.equal(validateOperation(operation({kind: 'context_performance', sourceId: raw, entityId: `r_${digest(raw)}`, payload: {weightKg: 75}})).sourceId, raw);
});
test('incompatible edits preserve a conflict instead of overwriting current data', () => {
  const current = {revision: 2, payload: {notes: 'device b'}};
  const result = decide(context({current}));
  assert.equal(result.conflict, true); assert.deepEqual(current.payload, {notes: 'device b'});
});
test('deleted accounts and old generations reject disconnected-device writes', () => {
  assert.throws(() => decide(context({account: {state: 'DELETED', generation: 2}})), /unavailable/);
  assert.throws(() => decide(context({account: {state: 'ACTIVE', generation: 2}})), /generation/);
});
test('an active workout cannot move between devices without resolution', () => {
  const result = decide(context({operation: operation({kind: 'ongoing_workout'}), lease: {deviceId: 'device-b', expiresAtEpochMs: now + 1000}}));
  assert.equal(result.reason, 'ACTIVE_WORKOUT_OWNED_BY_ANOTHER_DEVICE');
});
test('Free accepts five sessions in one week; rejects six, advanced programs and nutrition writes', () => {
  const payload = count => ({structure: 'SIMPLE', macrocycles: [{blocks: [{mesocycles: [{weeks: [{sessions: Array.from({length: count}, (_, n) => ({id: `s${n}`}))}]}]}]}]});
  assert.equal(decide(context({operation: operation({kind: 'program', payload: payload(5)})})).document.revision, 1);
  assert.throws(() => decide(context({operation: operation({kind: 'program', payload: payload(6)})})), /five/);
  assert.throws(() => decide(context({operation: operation({kind: 'nutrition_log'})})), /Pro/);
  assert.equal(decide(context({operation: operation({kind: 'nutrition_log', deleted: true, payload: null})})).document.deleted, true);
});
test('non-JSON payloads and sensitive credentials are rejected', () => {
  assert.throws(() => validateOperation(operation({payload: {weight: NaN}})), /Non-finite/);
  assert.throws(() => validateOperation(operation({kind: 'settings', payload: {apiKeys: {provider: 'secret'}}})), /Credentials/);
});
test('server verifies purchase account binding, pending purchases and paid cancellation expiry', () => {
  const accountBinding = digest('user-a');
  const snapshot = state => ({externalAccountIdentifiers: {obfuscatedExternalAccountId: accountBinding},
    subscriptionState: state, lineItems: [{productId: 'kpkn_pro', expiryTime: new Date(now + 1000).toISOString()}]});
  const args = {productId: 'kpkn_pro', accountBinding, now};
  assert.equal(validatePurchaseSnapshot(snapshot('SUBSCRIPTION_STATE_PENDING'), args).level, 'FREE');
  assert.equal(validatePurchaseSnapshot(snapshot('SUBSCRIPTION_STATE_CANCELED'), args).level, 'PRO');
  assert.equal(validatePurchaseSnapshot(snapshot('SUBSCRIPTION_STATE_ON_HOLD'), args).level, 'FREE');
  assert.throws(() => validatePurchaseSnapshot(snapshot('SUBSCRIPTION_STATE_ACTIVE'), {...args, accountBinding: digest('user-b')}), /different/);
});
