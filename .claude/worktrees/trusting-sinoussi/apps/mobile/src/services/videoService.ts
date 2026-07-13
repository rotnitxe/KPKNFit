export interface VideoAsset {
  uri: string;
  mimeType: string;
  fileName: string;
}

function inferVideoMime(uri: string) {
  const lower = uri.toLowerCase();
  if (lower.endsWith('.mov')) return 'video/quicktime';
  if (lower.endsWith('.mkv')) return 'video/x-matroska';
  return 'video/mp4';
}

export function buildVideoAsset(uri: string): VideoAsset {
  const chunks = uri.split('/');
  return {
    uri,
    mimeType: inferVideoMime(uri),
    fileName: chunks[chunks.length - 1] || `video-${Date.now()}.mp4`,
  };
}

