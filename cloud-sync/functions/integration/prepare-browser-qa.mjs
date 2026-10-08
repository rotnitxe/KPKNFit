import {initializeApp, deleteApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore} from 'firebase-admin/firestore';
import {SyncService} from '../src/sync-service.mjs';
import {writeFile} from 'node:fs/promises';

if (process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:19099' || process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18080') throw Error('Local emulators required');
const app = initializeApp({projectId: 'demo-kpkn-stability'});
const uid = 'browser-deletion-fixture';
const auth = getAuth(app);
try { await auth.createUser({uid, email: 'browser-deletion@example.test', password: 'EmulatorOnlyPassword1!'}); }
catch (error) { if (error.code !== 'auth/uid-already-exists') throw error; }
const sync = new SyncService(getFirestore(app));
await sync.openAccount(uid);
await sync.push(uid, {schemaVersion: 1, operationId: 'browser-fixture-log', entityId: 'browser-log',
  deviceId: 'browser-qa', accountGeneration: 1, baseRevision: 0, kind: 'workout_log',
  changedAtEpochMs: Date.now(), zoneId: 'America/Santiago', unitSystem: 'METRIC', deleted: false,
  payload: {id: 'browser-log', repetitions: 5}});
await writeFile('../../artifacts/stability-launch/browser-fixture.json', JSON.stringify({uid, email: 'browser-deletion@example.test', synthetic: true, project: 'demo-kpkn-stability'}));
await deleteApp(app);
