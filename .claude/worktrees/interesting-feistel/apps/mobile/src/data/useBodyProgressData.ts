import { useMemo } from 'react';
import { useBodyStore } from '../stores/bodyStore';
import { useChartDataFormatter } from './useChartDataFormatter';
import type { BodyWeightDataPoint, SkiaChartDataPoint } from '../types/charts';
import type { BodyProgressEntry } from '../types/workout';

/**
 * Hook adapter that bridges Zustand bodyStore with chart components.
 * Provides memoized, Skia-friendly transformed data to prevent unnecessary re-renders.
 */
export function useBodyProgressData() {
  const bodyProgress = useBodyStore((state) => state.bodyProgress);
  const addBodyLog = useBodyStore((state) => state.addBodyLog);
  const updateBodyLog = useBodyStore((state) => state.updateBodyLog);
  const deleteBodyLog = useBodyStore((state) => state.deleteBodyLog);

  const chartFormatter = useChartDataFormatter({
    dateFormat: 'medium',
    decimalPlaces: 1,
  });

  /**
   * Chronologically sorted logs (newest first) for list display.
   * Stable reference via useMemo to prevent list re-renders.
   */
  const sortedLogs = useMemo<BodyProgressEntry[]>(() => {
    return [...bodyProgress].sort(
      (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
    );
  }, [bodyProgress]);

  /**
   * Weight trend data formatted for Skia/victory-native charts.
   * Sorted chronologically (ascending) for proper line interpolation.
   * Returns stable array reference to prevent chart re-renders.
   */
  const weightTrendData = useMemo<BodyWeightDataPoint[]>(() => {
    return chartFormatter.formatBodyWeightTrend(bodyProgress);
  }, [bodyProgress, chartFormatter]);

  /**
   * Multi-metric chart data (weight, bodyFat, muscleMass) for Skia.
   * Useful for charts showing multiple Y-axis metrics.
   */
  const multiMetricChartData = useMemo<SkiaChartDataPoint<BodyProgressEntry>[]>(() => {
    return chartFormatter.formatMultiMetricTrend(bodyProgress, [
      'weight',
      'bodyFat',
      'muscleMass',
    ]);
  }, [bodyProgress, chartFormatter]);

  /**
   * Simplified data points for LineTrendChart component.
   * Matches the existing LineTrendPoint interface pattern.
   */
  const lineTrendPoints = useMemo(
    () =>
      weightTrendData.map((point) => ({
        key: point.original?.id ?? `weight_${point.dateMs}`,
        label: new Date(point.dateMs).toLocaleDateString('es-CL', {
          day: '2-digit',
          month: 'short',
        }),
        value: point.weight,
      })),
    [weightTrendData]
  );

  /**
   * Recent entries (last 5) for quick preview display.
   */
  const recentEntries = useMemo<BodyProgressEntry[]>(() => {
    return sortedLogs.slice(0, 5);
  }, [sortedLogs]);

  /**
   * Latest entry for displaying current stats.
   */
  const latestEntry = useMemo<BodyProgressEntry | null>(() => {
    return sortedLogs.length > 0 ? sortedLogs[0] : null;
  }, [sortedLogs]);

  return {
    // Data
    logs: sortedLogs,
    recentEntries,
    latestEntry,
    weightTrendData,
    multiMetricChartData,
    lineTrendPoints,
    // Actions
    addBodyLog,
    updateBodyLog,
    deleteBodyLog,
  };
}
