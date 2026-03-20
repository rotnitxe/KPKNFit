export interface ImageAsset {
  uri: string;
  mimeType: string;
  fileName: string;
}

function inferMimeType(uri: string) {
  const lower = uri.toLowerCase();
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.webp')) return 'image/webp';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  return 'image/jpeg';
}

function inferFileName(uri: string) {
  const chunks = uri.split('/');
  return chunks[chunks.length - 1] || `image-${Date.now()}.jpg`;
}

export function buildImageAsset(uri: string): ImageAsset {
  return {
    uri,
    mimeType: inferMimeType(uri),
    fileName: inferFileName(uri),
  };
}

