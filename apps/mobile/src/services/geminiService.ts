import type { Settings } from '../types/settings';
import {
  generateContent as generateBackendContent,
  generateContentStream as generateBackendStream,
} from './backendAIService';

export interface GenerateContentResponse {
  text: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  parts?: { text: string }[];
  content?: string;
}

function pickApiKey(settings: Settings) {
  return settings.apiKeys?.gemini;
}

function toPrompt(messages: ChatMessage[]) {
  return messages
    .map(message => {
      const content =
        message.content ??
        (message.parts ?? []).map(part => part.text).join(' ').trim();
      return `${message.role.toUpperCase()}: ${content}`;
    })
    .join('\n');
}

export async function generateContent(
  prompt: string,
  systemInstruction: string | undefined,
  jsonResponseSchema: unknown,
  settings: Settings,
): Promise<GenerateContentResponse> {
  if (!pickApiKey(settings)) {
    throw new Error('La clave API de Gemini no está configurada en ajustes.');
  }
  const response = await generateBackendContent({
    provider: 'gemini',
    prompt,
    systemInstruction,
    jsonMode: Boolean(jsonResponseSchema),
    temperature: settings.aiTemperature,
    maxTokens: settings.aiMaxTokens,
  });
  return { text: typeof response.text === 'string' ? response.text : '' };
}

export async function* generateContentStream(
  messages: ChatMessage[],
  systemInstruction: string | undefined,
  settings: Settings,
): AsyncGenerator<GenerateContentResponse> {
  if (!pickApiKey(settings)) {
    throw new Error('La clave API de Gemini no está configurada en ajustes.');
  }

  const stream = generateBackendStream({
    provider: 'gemini',
    prompt: toPrompt(messages),
    systemInstruction,
    temperature: settings.aiTemperature,
    maxTokens: settings.aiMaxTokens,
  });

  for await (const chunk of stream) {
    yield { text: chunk.text };
  }
}
