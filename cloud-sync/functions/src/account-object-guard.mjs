/** An upload accepted before deletion may finish after the purge. Its finalize event must purge that tail. */
export class AccountObjectGuard {
  constructor(database, storage) { this.db = database; this.storage = storage; }
  async finalized(object) {
    const match = /^accounts\/([^/]{1,128})\/([0-9]+)\/(?:data|media)\/[^/]{1,128}$/.exec(object.name ?? '');
    if (!match || !object.bucket || !object.generation) return {status: 'IGNORED'};
    const [, uid, generation] = match;
    const account = (await this.db.doc(`accounts/${uid}`).get()).data();
    if (account?.state === 'ACTIVE' && account.generationText === generation) return {status: 'OWNED'};
    try {
      await this.storage.bucket(object.bucket).file(object.name).delete({ifGenerationMatch: object.generation});
      return {status: 'PURGED'};
    } catch (error) {
      // Repeated events or a newer generation must never remove the newer object.
      if ([404, 412].includes(Number(error.code))) return {status: 'ALREADY_REMOVED_OR_REPLACED'};
      throw error;
    }
  }
}
