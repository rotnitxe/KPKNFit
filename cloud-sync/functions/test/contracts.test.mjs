import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {KINDS, canonicalJson, validateOperation} from '../src/protocol.mjs';

const fixtureUrl = new URL('../../contracts/fixtures.json', import.meta.url);
const schemaUrl = new URL('../../contracts/personal-operation.schema.json', import.meta.url);
test('the public schema and server share exactly the same entity kinds', async () => {
  const schema = JSON.parse(await readFile(schemaUrl, 'utf8'));
  assert.deepEqual(new Set(schema.properties.kind.enum), KINDS);
});
test('common fixtures preserve canonical units, original keys, origins, zones and tombstones', async () => {
  const fixtures = JSON.parse(await readFile(fixtureUrl, 'utf8'));
  fixtures.forEach(validateOperation);
  const body = fixtures.find(value => value.kind === 'body_observation');
  assert.equal(body.unitSystem, 'IMPERIAL'); assert.equal(body.payload.valueSi, 72.5); assert.equal(body.payload.unitSi, 'kg');
  assert.equal(body.payload.source, 'MANUAL'); assert.equal(body.payload.zoneId, 'America/Santiago');
  const context = fixtures.find(value => value.kind === 'context_performance');
  assert.equal(context.sourceId, context.payload.contextKey);
  assert.deepEqual(JSON.parse(canonicalJson(context)).payload.futureMetadata, context.payload.futureMetadata);
  assert.equal(fixtures.find(value => value.deleted).payload, null);
});
