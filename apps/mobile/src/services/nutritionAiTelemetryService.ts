import { getJsonValue, setJsonValue } from '../storage/mmkv';

const TELEMETRY_KEY = 'nutrition.ai.telemetry.v1';

export interface NutritionAiTelemetryEvent {
  id: string;
  createdAt: string;
  usedFallback: boolean;
  durationMs: number;
  requestLength: number;
  itemCount: number;
  runtimeError: string | null;
}

function readEvents() {
  return getJsonValue<NutritionAiTelemetryEvent[]>(TELEMETRY_KEY, []);
}

function writeEvents(events: NutritionAiTelemetryEvent[]) {
  setJsonValue(TELEMETRY_KEY, events);
}

export function recordNutritionAiTelemetry(event: Omit<NutritionAiTelemetryEvent, 'id' | 'createdAt'>) {
  const previous = readEvents();
  const next: NutritionAiTelemetryEvent = {
    id: `nutrition-ai-${Date.now()}-${Math.round(Math.random() * 1000)}`,
    createdAt: new Date().toISOString(),
    ...event,
  };
  writeEvents([next, ...previous].slice(0, 200));
  return next;
}

export function readNutritionAiTelemetry() {
  return readEvents();
}

export function clearNutritionAiTelemetry() {
  writeEvents([]);
}

export function summarizeNutritionAiTelemetry() {
  const events = readEvents();
  if (events.length === 0) {
    return {
      totalRuns: 0,
      fallbackRuns: 0,
      fallbackRate: 0,
      averageDurationMs: 0,
      lastError: null as string | null,
    };
  }

  const fallbackRuns = events.filter(event => event.usedFallback).length;
  const totalDuration = events.reduce((sum, event) => sum + event.durationMs, 0);
  const lastError =
    events.find(event => Boolean(event.runtimeError))?.runtimeError ?? null;

  return {
    totalRuns: events.length,
    fallbackRuns,
    fallbackRate: Math.round((fallbackRuns / events.length) * 100),
    averageDurationMs: Math.round(totalDuration / events.length),
    lastError,
  };
}

