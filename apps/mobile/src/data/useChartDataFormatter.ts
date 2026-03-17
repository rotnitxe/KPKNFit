import { useCallback, useMemo, useRef } from 'react';
import type { BodyProgressEntry } from '../types/workout';
import type {
  SkiaChartDataPoint,
  BodyWeightDataPoint,
  ChartFormatOptions,
} from '../types/charts';

/**
 * Map of metrics to their semantic color keys in the theme
 */
export const METRIC_COLORS: Record<string, keyof import('../theme/colors').KPKNColorTokens> = {
  weight: 'cyberCyan',
  bodyFat: 'cyberDanger',
  bodyFatPercentage: 'cyberDanger',
  muscleMass: 'cyberSuccess',
  muscleMassPercentage: 'cyberSuccess',
};

/**
 * Formats a Date object to a readable string based on style.
 */
function formatDateLabel(dateMs: number, style: 'short' | 'medium' | 'long' = 'medium'): string {
  const date = new Date(dateMs);

  switch (style) {
    case 'short':
      return date.toLocaleDateString('es-ES', { day: 'numeric', month: 'numeric' });
    case 'long':
      return date.toLocaleDateString('es-ES', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      });
    case 'medium':
    default:
      return date.toLocaleDateString('es-ES', {
        day: 'numeric',
        month: 'short'
      });
  }
}

/**
 * Rounds a number to specified decimal places.
 */
function roundTo(value: number, decimals: number = 1): number {
  const factor = Math.pow(10, decimals);
  return Math.round(value * factor) / factor;
}

/**
 * Simple reference equality check for array stability.
 * Compares length and first/last item timestamps for quick invalidation.
 */
function isLogsArrayStable(
  prev: BodyProgressEntry[] | null,
  next: BodyProgressEntry[]
): boolean {
  if (!prev || prev.length !== next.length) return false;
  if (prev.length === 0) return true;
  
  // Check first and last entry dates as a quick stability heuristic
  const prevFirst = prev[0]?.date;
  const prevLast = prev[prev.length - 1]?.date;
  const nextFirst = next[0]?.date;
  const nextLast = next[next.length - 1]?.date;
  
  return prevFirst === nextFirst && prevLast === nextLast;
}

/**
 * React hook that provides memoized chart data transformation functions.
 * Uses internal caching with reference stability checks to prevent 
 * unnecessary re-renders in victory-native/Skia charts.
 */
export function useChartDataFormatter(options?: ChartFormatOptions) {
  const dateFormat = options?.dateFormat ?? 'medium';
  const decimalPlaces = options?.decimalPlaces ?? 1;

  // Internal cache refs for weight trend data
  const weightTrendCacheRef = useRef<{
    input: BodyProgressEntry[] | null;
    output: BodyWeightDataPoint[] | null;
  }>({ input: null, output: null });

  // Internal cache refs for multi-metric trend data
  const multiMetricCacheRef = useRef<{
    input: { logs: BodyProgressEntry[]; metrics: string[] } | null;
    output: SkiaChartDataPoint<BodyProgressEntry>[] | null;
  }>({ input: null, output: null });

  // Internal cache refs for skia format conversion
  const skiaFormatCacheRef = useRef<{
    input: { data: BodyWeightDataPoint[]; xKey: string; yKeys: string[] } | null;
    output: SkiaChartDataPoint<BodyWeightDataPoint>[] | null;
  }>({ input: null, output: null });

  /**
   * Transforms body progress logs to Skia-friendly weight trend data.
   * Returns chronologically sorted array (oldest first) for proper line interpolation.
   * Uses internal caching to maintain stable array references.
   */
  const formatBodyWeightTrend = useCallback(
    (logs: BodyProgressEntry[]): BodyWeightDataPoint[] => {
      if (!logs || logs.length === 0) {
        weightTrendCacheRef.current = { input: null, output: null };
        return [];
      }

      // Quick stability check - avoid recalculation if input is referentially stable
      if (isLogsArrayStable(weightTrendCacheRef.current.input, logs)) {
        return weightTrendCacheRef.current.output!;
      }

      // Sort chronologically (ascending) for proper Skia interpolation
      const sorted = [...logs].sort(
        (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
      );

      const result = sorted
        .filter((entry) => entry.weight != null)
        .map((entry) => {
          const dateMs = Date.parse(entry.date);

          return {
            dateMs,
            weight: roundTo(entry.weight!, decimalPlaces),
            bodyFat: entry.bodyFatPercentage != null
              ? roundTo(entry.bodyFatPercentage, decimalPlaces)
              : undefined,
            muscleMass: entry.muscleMassPercentage != null
              ? roundTo(entry.muscleMassPercentage, decimalPlaces)
              : undefined,
            original: entry,
          };
        });

      // Update cache with new stable reference
      weightTrendCacheRef.current = { input: logs, output: result };
      return result;
    },
    [decimalPlaces]
  );

  /**
   * Transforms body progress logs to generic SkiaChartDataPoint format.
   * Supports multiple Y-axis metrics (weight, bodyFat, muscleMass, measurements).
   * Uses internal caching to maintain stable array references.
   */
  const formatMultiMetricTrend = useCallback(
    (
      logs: BodyProgressEntry[],
      metrics: string[]
    ): SkiaChartDataPoint<BodyProgressEntry>[] => {
      if (!logs || logs.length === 0 || metrics.length === 0) {
        multiMetricCacheRef.current = { input: null, output: null };
        return [];
      }

      // Quick stability check for both logs and metrics
      const prevInput = multiMetricCacheRef.current.input;
      if (
        prevInput &&
        isLogsArrayStable(prevInput.logs, logs) &&
        prevInput.metrics.length === metrics.length &&
        prevInput.metrics.every((m, i) => m === metrics[i])
      ) {
        return multiMetricCacheRef.current.output!;
      }

      // Sort chronologically (ascending) for proper Skia interpolation
      const sorted = [...logs].sort(
        (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
      );

      const result = sorted.map((entry) => {
        const dateMs = Date.parse(entry.date);
        const yVals: Record<string, number> = {};

        metrics.forEach((metric) => {
          let value: number | undefined;

          // Map common metric names to BodyProgressEntry fields
          switch (metric) {
            case 'weight':
              value = entry.weight;
              break;
            case 'bodyFat':
            case 'bodyFatPercentage':
              value = entry.bodyFatPercentage;
              break;
            case 'muscleMass':
            case 'muscleMassPercentage':
              value = entry.muscleMassPercentage;
              break;
            default:
              // Check measurements record if metric is a body part
              if (entry.measurements && metric in entry.measurements) {
                value = entry.measurements[metric];
              }
              break;
          }

          if (value != null) {
            yVals[metric] = roundTo(value, decimalPlaces);
          }
        });

        return {
          xVal: dateMs,
          yVals,
          label: formatDateLabel(dateMs, dateFormat),
          meta: entry,
        };
      });

      // Update cache with new stable reference
      multiMetricCacheRef.current = {
        input: { logs, metrics },
        output: result,
      };
      return result;
    },
    [dateFormat, decimalPlaces]
  );

  /**
   * Converts BodyWeightDataPoint array to SkiaChartDataPoint format
   * compatible with LineChartBase xKey/yKeys pattern.
   */
  const toSkiaFormat = useCallback(
    (
      data: BodyWeightDataPoint[],
      xKey = 'dateMs',
      yKeys = ['weight', 'bodyFat', 'muscleMass']
    ): SkiaChartDataPoint<BodyWeightDataPoint>[] => {
      if (!data || data.length === 0) {
        skiaFormatCacheRef.current = { input: null, output: null };
        return [];
      }

      // Quick stability check
      const prevInput = skiaFormatCacheRef.current.input;
      if (
        prevInput &&
        prevInput.data === data &&
        prevInput.xKey === xKey &&
        prevInput.yKeys === yKeys
      ) {
        return skiaFormatCacheRef.current.output!;
      }

      const result = data.map((point) => {
        const yVals: Record<string, number> = {};

        yKeys.forEach((key) => {
          const value = point[key as keyof BodyWeightDataPoint];
          if (typeof value === 'number') {
            yVals[key] = value;
          }
        });

        return {
          xVal: point.dateMs,
          yVals,
          label: formatDateLabel(point.dateMs, dateFormat),
          meta: point,
        };
      });

      // Update cache with new stable reference
      skiaFormatCacheRef.current = {
        input: { data, xKey, yKeys },
        output: result,
      };
      return result;
    },
    [dateFormat]
  );

  // Expose stable function references - memoized at hook level
  return useMemo(
    () => ({
      formatBodyWeightTrend,
      formatMultiMetricTrend,
      toSkiaFormat,
    }),
    [formatBodyWeightTrend, formatMultiMetricTrend, toSkiaFormat]
  );
}
