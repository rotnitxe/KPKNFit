const DEFAULT_BACKEND_URL = 'http://localhost:8000';

interface GenerateRequest {
  provider?: string;
  prompt: string;
  systemInstruction?: string;
  messages?: { role: string; parts?: { text: string }[]; content?: string }[];
  jsonMode?: boolean;
  temperature?: number;
  maxTokens?: number;
  model?: string;
  host?: string;
}

interface GenerateResponse {
  text?: string;
  [key: string]: unknown;
}

export interface BackendAIStatus {
  backend: boolean;
  providers?: {
    ollama?: {
      available?: boolean;
      host?: string;
      models?: string[];
      error?: string;
    };
  };
}

function resolveBackendUrl() {
  return DEFAULT_BACKEND_URL.replace(/\/$/, '');
}

export async function generateContent(req: GenerateRequest): Promise<GenerateResponse> {
  const response = await fetch(`${resolveBackendUrl()}/api/ai/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(`Backend AI error: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<GenerateResponse>;
}

export async function getBackendAIStatus(): Promise<BackendAIStatus> {
  const response = await fetch(`${resolveBackendUrl()}/api/ai/status`);
  if (!response.ok) {
    throw new Error(`Backend AI status error: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<BackendAIStatus>;
}

export async function* generateContentStream(
  req: Omit<GenerateRequest, 'jsonMode'>,
): AsyncGenerator<{ text: string }> {
  const response = await fetch(`${resolveBackendUrl()}/api/ai/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });

  if (!response.ok) {
    throw new Error(`Backend AI stream error: ${response.status}`);
  }
  const rawPayload = await response.text();
  const lines = rawPayload.split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.length === 0 || trimmed === 'data: [DONE]') continue;
    if (!trimmed.startsWith('data: ')) continue;
    try {
      const parsed = JSON.parse(trimmed.slice(6)) as { text?: string };
      if (typeof parsed.text === 'string' && parsed.text.length > 0) {
        yield { text: parsed.text };
      }
    } catch {
      // Ignore malformed chunks without killing the whole stream.
    }
  }
}
