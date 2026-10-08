import {createHash} from 'node:crypto';

export const SCHEMA_VERSION = 1;
export const MAX_OPERATION_BYTES = 512 * 1024;
export const MAX_DATA_BLOB_BYTES = 64 * 1024 * 1024;
export const KINDS = new Set([
  'program', 'workout_log', 'ongoing_workout', 'active_program', 'settings', 'competition',
  'body_observation', 'body_goal', 'nutrition_log', 'nutrition_plan', 'nutrition_active_state',
  'pantry_item', 'meal_template', 'custom_food', 'learned_resolution', 'nutrition_calibration',
  'daily_goal_snapshot', 'wellbeing', 'sleep', 'sleep_extended', 'post_session_feedback',
  'session_template', 'custom_exercise', 'workout_tag', 'context_performance',
  'global_performance', 'context_profile', 'replacement_decision', 'performance_range',
  'performance_snapshot', 'workout_media', 'workout_media_association',
  'setup_draft', 'setup_commit_receipt', 'adaptive_state',
]);
const ID = /^[A-Za-z0-9_.:-]{1,128}$/;
const PRO_KINDS = new Set(['nutrition_log', 'nutrition_plan', 'nutrition_active_state', 'pantry_item',
  'meal_template', 'custom_food', 'learned_resolution', 'nutrition_calibration', 'workout_media']);

export class ProtocolError extends Error {
  constructor(code, reason) { super(reason); this.code = code; }
}
export const fail = (code, message) => { throw new ProtocolError(code, message); };
export const digest = value => createHash('sha256').update(value).digest('hex');
export const canonicalJson = value => JSON.stringify(value, (_key, item) =>
  item && typeof item === 'object' && !Array.isArray(item) ?
    Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]])) : item);

function jsonValue(value, depth = 0) {
  if (depth > 48) fail('invalid-argument', 'Payload nesting limit exceeded');
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return;
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) fail('invalid-argument', 'Non-finite numeric value');
    return;
  }
  if (Array.isArray(value)) { value.forEach(item => jsonValue(item, depth + 1)); return; }
  if (typeof value !== 'object' || Object.getPrototypeOf(value) !== Object.prototype) fail('invalid-argument', 'Unsupported payload');
  for (const [key, item] of Object.entries(value)) {
    if (['__proto__', 'constructor', 'prototype'].includes(key)) fail('invalid-argument', 'Reserved key');
    jsonValue(item, depth + 1);
  }
}

export function validateOperation(operation, {materialized = false} = {}) {
  if (!operation || operation.schemaVersion !== SCHEMA_VERSION) fail('invalid-argument', 'Unsupported sync schema');
  for (const key of ['operationId', 'entityId', 'deviceId']) if (!ID.test(operation[key] ?? '')) fail('invalid-argument', 'Invalid identifier');
  if (operation.sourceId != null) {
    if (typeof operation.sourceId !== 'string' || !operation.sourceId.length || operation.sourceId.length > 4096) fail('invalid-argument', 'Invalid source identity');
    const expected = ID.test(operation.sourceId) ? operation.sourceId : `r_${digest(operation.sourceId)}`;
    if (operation.entityId !== expected) fail('invalid-argument', 'Entity identity does not match its source');
  }
  if (!KINDS.has(operation.kind)) fail('invalid-argument', 'Unsupported personal entity');
  if (!Number.isSafeInteger(operation.accountGeneration) || operation.accountGeneration < 1 ||
      !Number.isSafeInteger(operation.baseRevision) || operation.baseRevision < 0) fail('invalid-argument', 'Invalid revision');
  if (!Number.isSafeInteger(operation.changedAtEpochMs) || operation.changedAtEpochMs < 0 ||
      typeof operation.zoneId !== 'string' || operation.zoneId.length > 80) fail('invalid-argument', 'Invalid time metadata');
  const offset = /^(?:Z|(?:(?:UTC|GMT)?[+-])(\d{2}):(\d{2}))$/.exec(operation.zoneId);
  if (offset) {
    if (offset[1] && (+offset[1] > 18 || +offset[2] > 59 || (+offset[1] === 18 && +offset[2] !== 0))) fail('invalid-argument', 'Invalid time offset');
  } else {
    try { new Intl.DateTimeFormat('en', {timeZone: operation.zoneId}); }
    catch { fail('invalid-argument', 'Invalid time zone'); }
  }
  if (!['METRIC', 'IMPERIAL'].includes(operation.unitSystem) || typeof operation.deleted !== 'boolean') fail('invalid-argument', 'Invalid units or tombstone');
  if (operation.payloadRef != null && (operation.deleted || !/^[a-f0-9]{64}$/.test(operation.payloadRef.sha256 ?? '') ||
      !Number.isSafeInteger(operation.payloadRef.bytes) || operation.payloadRef.bytes < 1 || operation.payloadRef.bytes > MAX_DATA_BLOB_BYTES)) fail('invalid-argument', 'Invalid private data reference');
  if (operation.deleted ? operation.payload !== null : operation.payloadRef && !materialized ? operation.payload !== null :
      !operation.payload || typeof operation.payload !== 'object' || Array.isArray(operation.payload)) fail('invalid-argument', 'Invalid payload');
  jsonValue(operation);
  if (Buffer.byteLength(JSON.stringify(operation)) > (materialized ? MAX_DATA_BLOB_BYTES + MAX_OPERATION_BYTES : MAX_OPERATION_BYTES)) fail('invalid-argument', 'Operation exceeds the payload limit');
  if (operation.kind === 'settings' && !operation.deleted && operation.payload?.apiKeys &&
      Object.values(operation.payload.apiKeys).some(value => value !== null && value !== '')) fail('invalid-argument', 'Credentials must not be synchronized');
  if (!operation.deleted && operation.payload?.id != null &&
      ['program', 'workout_log', 'competition', 'body_observation', 'body_goal', 'nutrition_log', 'nutrition_plan',
        'pantry_item', 'meal_template', 'custom_food', 'session_template', 'custom_exercise', 'workout_media'].includes(operation.kind) &&
      String(operation.payload.id) !== (operation.sourceId ?? operation.entityId)) fail('invalid-argument', 'Record identity does not match its source');
  return operation;
}

export function isPro(entitlement, now) {
  return entitlement?.level === 'PRO' && ['ACTIVE', 'GRACE', 'CANCELED'].includes(entitlement.state) &&
    Number.isSafeInteger(entitlement.validUntilEpochMs) && entitlement.validUntilEpochMs > now;
}

export function checkCapability(operation, entitlement, now) {
  if (operation.deleted) return; // Control of owned data never requires Pro.
  if (isPro(entitlement, now)) return;
  if (PRO_KINDS.has(operation.kind)) fail('permission-denied', 'Creation or editing requires Pro');
  if (operation.kind === 'program') {
    const program = operation.payload;
    const array = value => {
      if (value == null) return [];
      if (!Array.isArray(value)) fail('invalid-argument', 'Invalid program structure');
      return value;
    };
    const weeks = array(program.macrocycles).flatMap(macro => array(macro.blocks)
      .flatMap(block => array(block.mesocycles).flatMap(meso => array(meso.weeks))));
    if ((program.structure ?? 'SIMPLE') !== 'SIMPLE' || weeks.length > 1 ||
        array(program.loops).length || program.calendarization || program.schedulePlan ||
        weeks.some(week => array(week.sessions).length > 5)) fail('permission-denied', 'Free supports one week with up to five sessions');
  }
}

/** Pure decision used inside the Firestore transaction. No clock from the client decides ownership. */
export function decide({uid, account, entitlement, operation, current, receipt, lease, now, approvedGuestImport = false}) {
  if (!uid) fail('unauthenticated', 'Sign in to synchronize');
  validateOperation(operation, {materialized: !!operation.payloadRef});
  if (!account || account.state !== 'ACTIVE' || account.generation !== operation.accountGeneration) fail('permission-denied', 'Account is unavailable or its generation changed');
  const fingerprint = digest(canonicalJson(operation));
  if (receipt) {
    if (receipt.fingerprint !== fingerprint) fail('already-exists', 'Operation ID was reused with different data');
    return {replay: true, receipt};
  }
  if (operation.kind === 'ongoing_workout' && !operation.deleted &&
      ((lease && lease.deviceId !== operation.deviceId && lease.expiresAtEpochMs > now) ||
       (current && !current.deleted && current.deviceId !== operation.deviceId))) {
    return {conflict: true, reason: 'ACTIVE_WORKOUT_OWNED_BY_ANOTHER_DEVICE', fingerprint};
  }
  const sameContent = current && current.deleted === operation.deleted && current.unitSystem === operation.unitSystem &&
    current.zoneId === operation.zoneId && (current.payloadRef && operation.payloadRef ?
      current.payloadRef.sha256 === operation.payloadRef.sha256 : canonicalJson(current.payload) === canonicalJson(operation.payload));
  if (sameContent) return {document: current, fingerprint};
  if (!approvedGuestImport) checkCapability(operation, entitlement, now);
  const revision = current?.revision ?? 0;
  if (operation.baseRevision !== revision) return {conflict: true, reason: 'INCOMPATIBLE_REVISION', fingerprint};
  return {document: {
    schemaVersion: SCHEMA_VERSION, kind: operation.kind, entityId: operation.entityId,
    sourceId: operation.sourceId ?? operation.entityId,
    ...(operation.payloadRef ? {payloadRef: operation.payloadRef} : {}),
    revision: revision + 1, deleted: operation.deleted, payload: operation.payload,
    changedAtEpochMs: operation.changedAtEpochMs, zoneId: operation.zoneId, unitSystem: operation.unitSystem,
    deviceId: operation.deviceId, confirmedAtEpochMs: now,
  }, fingerprint};
}

export function validatePurchaseSnapshot(snapshot, {productId, accountBinding, now}) {
  if (snapshot.externalAccountIdentifiers?.obfuscatedExternalAccountId !== accountBinding) fail('permission-denied', 'Purchase belongs to a different KPKN account');
  const items = (snapshot.lineItems ?? []).filter(item => item.productId === productId);
  if (!items.length) fail('invalid-argument', 'Unexpected product');
  const expiry = Math.max(...items.map(item => Date.parse(item.expiryTime ?? '')));
  const states = {
    SUBSCRIPTION_STATE_ACTIVE: 'ACTIVE', SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'GRACE',
    SUBSCRIPTION_STATE_CANCELED: 'CANCELED', SUBSCRIPTION_STATE_ON_HOLD: 'ON_HOLD',
    SUBSCRIPTION_STATE_PAUSED: 'PAUSED', SUBSCRIPTION_STATE_PENDING: 'PENDING',
    SUBSCRIPTION_STATE_EXPIRED: 'EXPIRED', SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: 'EXPIRED',
  };
  const state = states[snapshot.subscriptionState] ?? 'UNKNOWN';
  if (!Number.isFinite(expiry)) {
    if (state === 'PENDING') return {level: 'FREE', state, validUntilEpochMs: 0, confirmedAtEpochMs: now};
    fail('invalid-argument', 'Missing verified expiry');
  }
  return {level: ['ACTIVE', 'GRACE', 'CANCELED'].includes(state) && expiry > now ? 'PRO' : 'FREE',
    state, validUntilEpochMs: expiry, confirmedAtEpochMs: now};
}
