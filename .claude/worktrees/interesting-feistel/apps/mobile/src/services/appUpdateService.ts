export interface AppUpdateStatus {
  updateAvailable: boolean;
  currentVersion: string;
  latestVersion: string | null;
  checkedAt: string;
  source: 'remote' | 'local';
}

function compareVersions(current: string, latest: string) {
  const currentParts = current.split('.').map(value => Number(value) || 0);
  const latestParts = latest.split('.').map(value => Number(value) || 0);
  const maxLength = Math.max(currentParts.length, latestParts.length);
  for (let index = 0; index < maxLength; index += 1) {
    const currentValue = currentParts[index] ?? 0;
    const latestValue = latestParts[index] ?? 0;
    if (latestValue > currentValue) return true;
    if (latestValue < currentValue) return false;
  }
  return false;
}

export async function checkForAppUpdate(options?: {
  currentVersion?: string;
  manifestUrl?: string;
}): Promise<AppUpdateStatus> {
  const currentVersion = options?.currentVersion ?? '0.0.1';
  if (!options?.manifestUrl) {
    return {
      updateAvailable: false,
      currentVersion,
      latestVersion: null,
      checkedAt: new Date().toISOString(),
      source: 'local',
    };
  }

  try {
    const response = await fetch(options.manifestUrl, { method: 'GET' });
    const payload = (await response.json()) as { version?: string };
    const latestVersion = payload.version ?? null;
    return {
      updateAvailable: latestVersion ? compareVersions(currentVersion, latestVersion) : false,
      currentVersion,
      latestVersion,
      checkedAt: new Date().toISOString(),
      source: 'remote',
    };
  } catch {
    return {
      updateAvailable: false,
      currentVersion,
      latestVersion: null,
      checkedAt: new Date().toISOString(),
      source: 'local',
    };
  }
}
