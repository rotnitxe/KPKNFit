/**
 * Chart types for victory-native / Skia-based charts.
 * These types bridge the gap between Zustand store data and the chart components.
 */

import type { BodyProgressEntry } from './workout';

/**
 * Base data point format expected by victory-native CartesianChart.
 * xVal must be a number (timestamp) for proper Skia interpolation.
 */
export interface SkiaChartDataPoint<TMeta = unknown> {
  /** Numeric X value - typically a timestamp (ms) for time-series charts */
  xVal: number;
  /** Y values keyed by metric name (e.g., { weight: 75.5, bodyFat: 12.3 }) */
  yVals: Record<string, number>;
  /** Optional formatted label for tooltips/axes */
  label?: string;
  /** Optional reference to original source data */
  meta?: TMeta;
}

/**
 * Specialized data point for body weight trend charts.
 */
export interface BodyWeightDataPoint {
  /** Timestamp in milliseconds (Date.parse() compatible) */
  dateMs: number;
  /** Weight value in kg */
  weight: number;
  /** Optional body fat percentage */
  bodyFat?: number;
  /** Optional muscle mass percentage */
  muscleMass?: number;
  /** Original log entry reference */
  original?: BodyProgressEntry;
}

/**
 * Formatted date label options for chart axes.
 */
export type DateFormatStyle = 'short' | 'medium' | 'long';

/**
 * Configuration for chart data formatting.
 */
export interface ChartFormatOptions {
  /** Date format style for labels */
  dateFormat?: DateFormatStyle;
  /** Number of decimal places for values */
  decimalPlaces?: number;
  /** Sort order (default: chronological ascending for charts) */
  sortOrder?: 'asc' | 'desc';
}

/**
 * Generic transformer function type for chart data.
 */
export type ChartDataTransformer<TInput, TOutput> = (
  data: TInput,
  options?: ChartFormatOptions
) => TOutput;
