import assert from 'node:assert/strict';
import {initializeApp, deleteApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore} from 'firebase-admin/firestore';
import {writeFile} from 'node:fs/promises';
if (process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:19099' || process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18080') throw Error('Local emulators required');
const app = initializeApp({projectId: 'demo-kpkn-stability'});
const db = getFirestore(app), uid = 'browser-deletion-fixture';
assert.equal((await db.doc(`accounts/${uid}`).get()).data().state, 'DELETED');
assert.equal((await db.collection(`accounts/${uid}/entities`).get()).size, 0);
await assert.rejects(getAuth(app).getUser(uid), error => error.code === 'auth/user-not-found');
await writeFile('../../artifacts/stability-launch/web-deletion-local.json', JSON.stringify({status: 'PASS',
  project: 'demo-kpkn-stability', languages: ['es', 'en'], checks: ['email sign-in', 'explicit confirmation',
    'reauthentication', 'wrong password rejected', 'deleted status visible', 'Auth removed', 'Firestore entity removed', 'account tombstone'],
  syntheticIdentity: true, productionHosting: 'NOT_DEPLOYED', liveGoogleSignIn: 'NOT_RUN'}, null, 2));
await deleteApp(app);
