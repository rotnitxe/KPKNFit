import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {Readable} from 'node:stream';
import {SyncService} from '../src/sync-service.mjs';
import {digest} from '../src/protocol.mjs';

test('complete HEIC HEIF AVIF photos pass verified server media references without changing their bytes', async () => {
  const uid = 'synthetic-media-user';
  for (const [extension, contentType] of [['heic', 'image/heic'], ['heif', 'image/heif'], ['avif', 'image/avif']]) {
    const bytes = await readFile(new URL(`../../../android-native/app/src/test/resources/media-formats/sample.${extension}`, import.meta.url));
    const sha256 = digest(bytes);
    const reference = {role: 'main', sha256, bytes: bytes.length, contentType};
    const bucket = {file(path) {
      assert.equal(path, `accounts/${uid}/1/media/${sha256}`);
      return {getMetadata: async () => [{size: bytes.length, contentType, metadata: {ownerUid: uid, fileId: sha256}}],
        createReadStream: () => Readable.from([bytes.subarray(0, 16), bytes.subarray(16)])};
    }};
    const service = new SyncService(null, () => 0, bucket);
    await service.verifyMedia(uid, {kind: 'workout_media', accountGeneration: 1, payload: {mediaFiles: [reference]}});
    await assert.rejects(service.verifyMedia(uid, {kind: 'workout_media', accountGeneration: 1,
      payload: {mediaFiles: [{...reference, contentType: 'video/mp4'}]}}), /type|ownership|size/i);
  }
});
