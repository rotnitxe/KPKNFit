export interface CameraCaptureResult {
  uri: string;
  mimeType: string;
  width?: number;
  height?: number;
}

function unsupported(action: string): never {
  throw new Error(`[camera] ${action} no esta configurado en esta build RN.`);
}

export async function capturePhoto(): Promise<CameraCaptureResult> {
  return unsupported('capturePhoto');
}

export async function pickPhotoFromLibrary(): Promise<CameraCaptureResult> {
  return unsupported('pickPhotoFromLibrary');
}

