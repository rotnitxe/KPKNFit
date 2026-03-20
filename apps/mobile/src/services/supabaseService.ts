type SupabaseResult<T = unknown> = Promise<{ data: T | null; error: Error | null }>;

function buildUnsupportedResult<T>(): SupabaseResult<T> {
  return Promise.resolve({
    data: null,
    error: new Error('Supabase no está habilitado en esta build RN.'),
  });
}

export const supabase = {
  from(_table: string) {
    return {
      select<T = unknown>() {
        return buildUnsupportedResult<T[]>();
      },
      upsert<T = unknown>(_payload: T | T[]) {
        return buildUnsupportedResult<T[]>();
      },
      insert<T = unknown>(_payload: T | T[]) {
        return buildUnsupportedResult<T[]>();
      },
      update<T = unknown>(_payload: Partial<T>) {
        return {
          eq(_column: string, _value: unknown) {
            return buildUnsupportedResult<T[]>();
          },
        };
      },
      delete() {
        return {
          eq(_column: string, _value: unknown) {
            return buildUnsupportedResult<unknown[]>();
          },
        };
      },
    };
  },
};
