/**
 * Backend AI Service – Client-side proxy to the FastAPI backend.
 * Replaces direct calls to Gemini/GPT/DeepSeek APIs.
 * API keys stay on the server; the client only sends prompts and context.
 */

const BACKEND_URL = import.meta?.env?.VITE_BACKEND_URL || 'http://localhost:8000';

interface GenerateRequest {
    provider?: string;
    prompt: string;
    systemInstruction?: string;
    messages?: { role: string; parts?: { text: string }[]; content?: string }[];
    jsonMode?: boolean;
    temperature?: number;
    maxTokens?: number;
}

interface GenerateResponse {
    text?: string;
    [key: string]: any;
}

export async function generateContent(req: GenerateRequest): Promise<GenerateResponse> {
    const res = await fetch(`${BACKEND_URL}/api/ai/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
    });
    if (!res.ok) throw new Error(`Backend AI error: ${res.status} ${res.statusText}`);
    return res.json();
}

/**
 * SSE streaming generator for chat-style responses.
 * Yields { text: string } chunks as they arrive from the backend.
 */
export async function* generateContentStream(
    req: Omit<GenerateRequest, 'jsonMode'>
): AsyncGenerator<{ text: string }> {
    const res = await fetch(`${BACKEND_URL}/api/ai/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
    });

    if (!res.ok) throw new Error(`Backend AI stream error: ${res.status}`);
    if (!res.body) throw new Error('No response body for streaming');

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                const trimmed = line.trim();
                if (trimmed === 'data: [DONE]') return;
                if (trimmed.startsWith('data: ')) {
                    try {
                        const data = JSON.parse(trimmed.slice(6));
                        if (data.text) yield { text: data.text };
                    } catch { /* skip malformed chunks */ }
                }
            }
        }
    } finally {
        reader.releaseLock();
    }
}
