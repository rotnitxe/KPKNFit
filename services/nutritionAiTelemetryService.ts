import type { ParsedMealDescription } from '../types';
import type { LocalAiBackend, LocalAiDeliveryMode, LocalAiStatus } from './localAiService';
import { storageService } from './storageService';

const RUNS_KEY = 'nutrition-ai-telemetry-runs';
const CORRECTIONS_KEY = 'nutrition-ai-telemetry-corrections';
const MAX_RUNS = 60;
const MAX_CORRECTIONS = 120;

export type NutritionAiTraceStage = 'interpreting' | 'matching' | 'estimating';
export type NutritionAiTraceStatus = 'success' | 'failed' | 'cancelled';
export type NutritionAiTraceSource = 'rules' | 'database' | 'user-memory' | 'local-ai-estimate' | 'local-heuristic' | 'review';
export type NutritionAiTraceResolution = 'pending' | 'resolved' | 'needs_review' | 'unresolved';

export interface NutritionAiTelemetryTagSnapshot {
    resolutionStatus: NutritionAiTraceResolution;
    analysisSource?: NutritionAiTraceSource;
    analysisConfidence?: number;
    reviewRequired?: boolean;
}

export interface NutritionAiTelemetryRun {
    id: string;
    startedAt: string;
    completedAt: string;
    status: NutritionAiTraceStatus;
    descriptionLength: number;
    localAiEnabled: boolean;
    localAiAvailable: boolean;
    localAiModelReady: boolean;
    deliveryMode: LocalAiDeliveryMode | null;
    backend: LocalAiBackend | null;
    requestedModel: string | null;
    modelVersion: string | null;
    analysisEngine: ParsedMealDescription['analysisEngine'] | 'unknown';
    totalElapsedMs: number;
    stageDurationsMs: Record<NutritionAiTraceStage, number>;
    itemCount: number;
    resolvedCount: number;
    reviewCount: number;
    unresolvedCount: number;
    containsEstimatedItems: boolean;
    requiresReview: boolean;
    sourceBreakdown: Record<NutritionAiTraceSource, number>;
    averageConfidence: number | null;
    minConfidence: number | null;
    maxConfidence: number | null;
    reanalysis: boolean;
    errorMessage: string | null;
}

export interface NutritionAiManualCorrectionEvent {
    id: string;
    createdAt: string;
    originalTag: string;
    selectedFoodName: string;
    fromSource: NutritionAiTraceSource | null;
    confidence: number | null;
    rememberQuery: boolean;
    reviewRequired: boolean;
}

export interface NutritionAiTelemetrySummary {
    runCount: number;
    successCount: number;
    failureCount: number;
    cancelledCount: number;
    averageTotalElapsedMs: number;
    p95TotalElapsedMs: number;
    averageConfidence: number | null;
    reviewRate: number;
    estimatedItemRate: number;
    sourceBreakdown: Record<NutritionAiTraceSource, number>;
    correctionsCount: number;
}

interface NutritionAiTraceContext {
    descriptionLength: number;
    localAiEnabled: boolean;
    requestedModel: string | null;
    reanalysis: boolean;
}

interface FinalizeTraceInput {
    parsed: ParsedMealDescription | null;
    tags?: NutritionAiTelemetryTagSnapshot[];
    errorMessage?: string | null;
}

let cachedRuns: NutritionAiTelemetryRun[] | null = null;
let cachedCorrections: NutritionAiManualCorrectionEvent[] | null = null;

function nowIso(): string {
    return new Date().toISOString();
}

function clampConfidence(value: number | undefined): number | null {
    if (typeof value !== 'number' || !Number.isFinite(value)) return null;
    return Math.max(0, Math.min(1, value));
}

function average(values: number[]): number {
    if (!values.length) return 0;
    return values.reduce((acc, value) => acc + value, 0) / values.length;
}

function percentile(values: number[], pct: number): number {
    if (!values.length) return 0;
    const sorted = [...values].sort((a, b) => a - b);
    const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((pct / 100) * sorted.length) - 1));
    return sorted[idx];
}

async function loadRuns(): Promise<NutritionAiTelemetryRun[]> {
    if (cachedRuns) return cachedRuns;
    const stored = await storageService.get<NutritionAiTelemetryRun[]>(RUNS_KEY);
    cachedRuns = Array.isArray(stored) ? stored : [];
    return cachedRuns;
}

async function loadCorrections(): Promise<NutritionAiManualCorrectionEvent[]> {
    if (cachedCorrections) return cachedCorrections;
    const stored = await storageService.get<NutritionAiManualCorrectionEvent[]>(CORRECTIONS_KEY);
    cachedCorrections = Array.isArray(stored) ? stored : [];
    return cachedCorrections;
}

async function persistRuns(runs: NutritionAiTelemetryRun[]): Promise<void> {
    cachedRuns = runs;
    await storageService.set(RUNS_KEY, runs);
}

async function persistCorrections(corrections: NutritionAiManualCorrectionEvent[]): Promise<void> {
    cachedCorrections = corrections;
    await storageService.set(CORRECTIONS_KEY, corrections);
}

function buildEmptySourceBreakdown(): Record<NutritionAiTraceSource, number> {
    return {
        rules: 0,
        database: 0,
        'user-memory': 0,
        'local-ai-estimate': 0,
        'local-heuristic': 0,
        review: 0,
    };
}

function summarizeTags(tags: NutritionAiTelemetryTagSnapshot[] | undefined) {
    const safeTags = Array.isArray(tags) ? tags : [];
    const sourceBreakdown = buildEmptySourceBreakdown();
    const confidences: number[] = [];
    let resolvedCount = 0;
    let reviewCount = 0;
    let unresolvedCount = 0;
    let estimatedCount = 0;

    safeTags.forEach((tag) => {
        const source = tag.analysisSource ?? 'rules';
        sourceBreakdown[source] += 1;
        const confidence = clampConfidence(tag.analysisConfidence);
        if (confidence != null) confidences.push(confidence);
        if (source === 'local-ai-estimate') estimatedCount += 1;

        if (tag.resolutionStatus === 'resolved') {
            resolvedCount += 1;
        } else if (tag.resolutionStatus === 'needs_review') {
            reviewCount += 1;
        } else if (tag.resolutionStatus === 'unresolved' || tag.resolutionStatus === 'pending') {
            unresolvedCount += 1;
        }
    });

    return {
        itemCount: safeTags.length,
        resolvedCount,
        reviewCount,
        unresolvedCount,
        containsEstimatedItems: estimatedCount > 0,
        sourceBreakdown,
        averageConfidence: confidences.length ? average(confidences) : null,
        minConfidence: confidences.length ? Math.min(...confidences) : null,
        maxConfidence: confidences.length ? Math.max(...confidences) : null,
    };
}

async function appendRun(run: NutritionAiTelemetryRun): Promise<void> {
    const runs = await loadRuns();
    const next = [run, ...runs].slice(0, MAX_RUNS);
    await persistRuns(next);
}

export async function listNutritionAiTelemetryRuns(): Promise<NutritionAiTelemetryRun[]> {
    return [...await loadRuns()];
}

export async function listNutritionAiManualCorrections(): Promise<NutritionAiManualCorrectionEvent[]> {
    return [...await loadCorrections()];
}

export async function clearNutritionAiTelemetry(): Promise<void> {
    await Promise.all([
        persistRuns([]),
        persistCorrections([]),
    ]);
}

export async function recordNutritionAiManualCorrection(input: Omit<NutritionAiManualCorrectionEvent, 'id' | 'createdAt'>): Promise<void> {
    const corrections = await loadCorrections();
    const nextEvent: NutritionAiManualCorrectionEvent = {
        ...input,
        id: crypto.randomUUID(),
        createdAt: nowIso(),
    };
    const next = [nextEvent, ...corrections].slice(0, MAX_CORRECTIONS);
    await persistCorrections(next);
}

export async function getNutritionAiTelemetrySummary(): Promise<NutritionAiTelemetrySummary> {
    const [runs, corrections] = await Promise.all([
        loadRuns(),
        loadCorrections(),
    ]);

    const sourceBreakdown = buildEmptySourceBreakdown();
    let confidenceAccumulator = 0;
    let confidenceCount = 0;
    let reviewRuns = 0;
    let estimatedItems = 0;
    let totalItems = 0;

    runs.forEach((run) => {
        Object.entries(run.sourceBreakdown).forEach(([key, value]) => {
            sourceBreakdown[key as NutritionAiTraceSource] += value;
        });
        if (run.averageConfidence != null) {
            confidenceAccumulator += run.averageConfidence;
            confidenceCount += 1;
        }
        if (run.requiresReview || run.reviewCount > 0) {
            reviewRuns += 1;
        }
        estimatedItems += run.sourceBreakdown['local-ai-estimate'];
        totalItems += run.itemCount;
    });

    return {
        runCount: runs.length,
        successCount: runs.filter((run) => run.status === 'success').length,
        failureCount: runs.filter((run) => run.status === 'failed').length,
        cancelledCount: runs.filter((run) => run.status === 'cancelled').length,
        averageTotalElapsedMs: runs.length ? average(runs.map((run) => run.totalElapsedMs)) : 0,
        p95TotalElapsedMs: runs.length ? percentile(runs.map((run) => run.totalElapsedMs), 95) : 0,
        averageConfidence: confidenceCount ? confidenceAccumulator / confidenceCount : null,
        reviewRate: runs.length ? reviewRuns / runs.length : 0,
        estimatedItemRate: totalItems ? estimatedItems / totalItems : 0,
        sourceBreakdown,
        correctionsCount: corrections.length,
    };
}

class NutritionAiTrace {
    private readonly id = crypto.randomUUID();
    private readonly startedAtMs = Date.now();
    private readonly startedAtIso = nowIso();
    private runtimeStatus: LocalAiStatus | null = null;
    private readonly stageMarks: Array<{ stage: NutritionAiTraceStage; at: number }> = [];
    private finalized = false;

    constructor(private readonly context: NutritionAiTraceContext) { }

    setRuntimeStatus(status: LocalAiStatus | null): void {
        if (this.finalized) return;
        this.runtimeStatus = status;
    }

    markStage(stage: NutritionAiTraceStage): void {
        if (this.finalized) return;
        const now = Date.now();
        const last = this.stageMarks[this.stageMarks.length - 1];
        if (last?.stage === stage) return;
        this.stageMarks.push({ stage, at: now });
    }

    complete(input: FinalizeTraceInput): void {
        this.finalize('success', input);
    }

    fail(errorMessage: string, input: Partial<Omit<FinalizeTraceInput, 'errorMessage'>> = {}): void {
        this.finalize('failed', { parsed: input.parsed ?? null, tags: input.tags, errorMessage });
    }

    cancel(input: Partial<Omit<FinalizeTraceInput, 'errorMessage'>> = {}): void {
        this.finalize('cancelled', { parsed: input.parsed ?? null, tags: input.tags, errorMessage: null });
    }

    private finalize(status: NutritionAiTraceStatus, input: FinalizeTraceInput): void {
        if (this.finalized) return;
        this.finalized = true;

        const completedAtMs = Date.now();
        const completedAtIso = nowIso();
        const stageDurationsMs: Record<NutritionAiTraceStage, number> = {
            interpreting: 0,
            matching: 0,
            estimating: 0,
        };

        this.stageMarks.forEach((mark, index) => {
            const nextMark = this.stageMarks[index + 1];
            const endAt = nextMark?.at ?? completedAtMs;
            stageDurationsMs[mark.stage] += Math.max(0, endAt - mark.at);
        });

        const tagSummary = summarizeTags(input.tags);
        const parsed = input.parsed;
        const run: NutritionAiTelemetryRun = {
            id: this.id,
            startedAt: this.startedAtIso,
            completedAt: completedAtIso,
            status,
            descriptionLength: this.context.descriptionLength,
            localAiEnabled: this.context.localAiEnabled,
            localAiAvailable: Boolean(this.runtimeStatus?.available),
            localAiModelReady: Boolean(this.runtimeStatus?.modelReady),
            deliveryMode: this.runtimeStatus?.deliveryMode ?? null,
            backend: this.runtimeStatus?.backend ?? null,
            requestedModel: this.context.requestedModel,
            modelVersion: parsed?.modelVersion ?? this.runtimeStatus?.modelVersion ?? this.context.requestedModel,
            analysisEngine: parsed?.analysisEngine ?? 'unknown',
            totalElapsedMs: Math.max(0, completedAtMs - this.startedAtMs),
            stageDurationsMs,
            itemCount: tagSummary.itemCount,
            resolvedCount: tagSummary.resolvedCount,
            reviewCount: tagSummary.reviewCount,
            unresolvedCount: tagSummary.unresolvedCount,
            containsEstimatedItems: parsed?.containsEstimatedItems ?? tagSummary.containsEstimatedItems,
            requiresReview: parsed?.requiresReview ?? tagSummary.reviewCount > 0,
            sourceBreakdown: tagSummary.sourceBreakdown,
            averageConfidence: tagSummary.averageConfidence,
            minConfidence: tagSummary.minConfidence,
            maxConfidence: tagSummary.maxConfidence,
            reanalysis: this.context.reanalysis,
            errorMessage: input.errorMessage ?? null,
        };

        void appendRun(run);
    }
}

export function startNutritionAiTrace(context: NutritionAiTraceContext): NutritionAiTrace {
    return new NutritionAiTrace(context);
}

function registerDebugBridge(): void {
    if (typeof window === 'undefined') return;
    const target = window as typeof window & {
        __kpknNutritionAiTelemetry?: {
            runs: () => Promise<NutritionAiTelemetryRun[]>;
            summary: () => Promise<NutritionAiTelemetrySummary>;
            corrections: () => Promise<NutritionAiManualCorrectionEvent[]>;
            clear: () => Promise<void>;
        };
    };

    target.__kpknNutritionAiTelemetry = {
        runs: listNutritionAiTelemetryRuns,
        summary: getNutritionAiTelemetrySummary,
        corrections: listNutritionAiManualCorrections,
        clear: clearNutritionAiTelemetry,
    };
}

registerDebugBridge();
