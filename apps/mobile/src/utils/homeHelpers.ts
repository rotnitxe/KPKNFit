
import type { BodyProgressEntry } from '../types/workout';
import type { SavedNutritionEntry } from '../types/nutrition';

export function getGreeting(userName?: string): string {
  const hour = new Date().getHours();
  let greeting = '';

  if (hour >= 5 && hour < 12) {
    greeting = 'Buenos días';
  } else if (hour >= 12 && hour < 20) {
    greeting = 'Buenas tardes';
  } else {
    greeting = 'Buenas noches';
  }

  return (userName && userName !== 'Usuario') ? `${greeting}, ${userName}` : greeting;
}

export function calculateWeightDelta(bodyProgress: BodyProgressEntry[]): { delta: number; days: number } | null {
  if (bodyProgress.length < 2) return null;

  // Sort by date descending
  const sorted = [...bodyProgress].sort((a, b) => 
    new Date(b.date).getTime() - new Date(a.date).getTime()
  );

  const latest = sorted[0];
  const previous = sorted[1];

  if (typeof latest.weight !== 'number' || typeof previous.weight !== 'number') return null;

  const delta = Math.round((latest.weight - previous.weight) * 10) / 10;
  const diffTime = Math.abs(new Date(latest.date).getTime() - new Date(previous.date).getTime());
  const days = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  return { delta, days };
}

export interface DailyMacroTotals {
  dateKey: string;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
}

export function buildLast7NutritionSeries(logs: SavedNutritionEntry[]): DailyMacroTotals[] {
  const series: DailyMacroTotals[] = [];
  const today = new Date();
  
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const dateKey = d.toISOString().slice(0, 10);
    
    const dayLogs = logs.filter(log => log.createdAt.slice(0, 10) === dateKey);
    const totals = dayLogs.reduce((acc, log) => ({
      calories: acc.calories + (log.totals?.calories || 0),
      protein: acc.protein + (log.totals?.protein || 0),
      carbs: acc.carbs + (log.totals?.carbs || 0),
      fats: acc.fats + (log.totals?.fats || 0),
    }), { calories: 0, protein: 0, carbs: 0, fats: 0 });

    series.push({
      dateKey,
      ...totals
    });
  }

  return series;
}

export function buildBodyWeightSeries(progress: BodyProgressEntry[]): { key: string; label: string; value: number }[] {
  const filtered = progress
    .filter(p => typeof p.weight === 'number')
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

  const last12 = filtered.slice(-12);

  return last12.map(p => {
    const d = new Date(p.date);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    return {
      key: p.id,
      label: `${day}/${month}`,
      value: p.weight!
    };
  });
}
