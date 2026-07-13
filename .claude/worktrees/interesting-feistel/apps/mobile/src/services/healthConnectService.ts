export interface HealthConnectStatus {
  available: boolean;
  connected: boolean;
  source: 'health-connect';
  message: string;
}

export async function getHealthConnectStatus(): Promise<HealthConnectStatus> {
  return {
    available: false,
    connected: false,
    source: 'health-connect',
    message: 'Health Connect aun no esta integrado en esta version RN.',
  };
}

export async function requestHealthConnectPermissions() {
  return {
    granted: false,
    message: 'Permisos de Health Connect no disponibles en RN.',
  };
}

