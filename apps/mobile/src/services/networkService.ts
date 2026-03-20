export interface NetworkStatusSnapshot {
  isOnline: boolean;
  checkedAt: string;
  latencyMs: number | null;
  source: 'ping' | 'manual';
}

let lastSnapshot: NetworkStatusSnapshot = {
  isOnline: true,
  checkedAt: new Date().toISOString(),
  latencyMs: null,
  source: 'manual',
};

async function ping(url: string, timeoutMs: number) {
  const startedAt = Date.now();
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    await fetch(url, {
      method: 'GET',
      signal: controller.signal,
    });
    return {
      ok: true,
      latencyMs: Date.now() - startedAt,
    };
  } catch {
    return {
      ok: false,
      latencyMs: null,
    };
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function getNetworkStatus(): Promise<NetworkStatusSnapshot> {
  const result = await ping('https://www.gstatic.com/generate_204', 4000);
  lastSnapshot = {
    isOnline: result.ok,
    checkedAt: new Date().toISOString(),
    latencyMs: result.latencyMs,
    source: 'ping',
  };
  return lastSnapshot;
}

export function readLastNetworkStatus() {
  return lastSnapshot;
}
