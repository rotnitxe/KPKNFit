// components/GymLeaderboard.tsx
import React, { useMemo } from 'react';
import { WorkoutLog } from '../types';
import Card from './ui/Card';
import { MapPinIcon } from './icons';

interface GymLeaderboardProps {
  history: WorkoutLog[];
}

const GymLeaderboard: React.FC<GymLeaderboardProps> = ({ history }) => {
  const gymStats = useMemo(() => {
    if (!history || history.length === 0) {
      return [];
    }
    
    const counts: Record<string, number> = {};
    history.forEach(log => {
      if (log.gymName) {
        counts[log.gymName] = (counts[log.gymName] || 0) + 1;
      }
    });

    return Object.entries(counts)
      .map(([name, visits]) => ({ name, visits }))
      .sort((a, b) => b.visits - a.visits)
      .slice(0, 3); // Top 3
  }, [history]);

  if (gymStats.length === 0) {
    return null; // Don't render if no gym data
  }

  return (
    <Card>
      <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
        <MapPinIcon /> Gimnasios Más Frecuentes
      </h3>
      <ul className="space-y-3">
        {gymStats.map((gym, index) => (
          <li key={gym.name} className="flex justify-between items-center text-slate-300">
            <span className="font-semibold">
              <span className="font-bold text-sm mr-2 text-[var(--primary-color-400)]">{index + 1}.</span>
              {gym.name}
            </span>
            <span className="text-sm bg-slate-800 px-2 py-1 rounded-md font-mono">{gym.visits} {gym.visits === 1 ? 'visita' : 'visitas'}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
};

export default GymLeaderboard;