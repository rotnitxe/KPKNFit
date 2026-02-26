// components/AchievementsView.tsx
import React, { useMemo } from 'react';
import { AchievementUnlock, Achievement } from '../types';
import { ACHIEVEMENTS_LIST } from '../data/achievements';
import Card from './ui/Card';
import * as Icons from './icons';

interface AchievementsViewProps {
  unlocked: AchievementUnlock[];
}

const AchievementsView: React.FC<AchievementsViewProps> = ({ unlocked }) => {
  const unlockedIds = useMemo(() => {
    const ids = new Set<string>();
    if (Array.isArray(unlocked)) {
        for (const u of unlocked) {
            ids.add(u.achievementId);
        }
    }
    return ids;
  }, [unlocked]);
  
  const unlockedMap = useMemo(() => {
    const map = new Map<string, AchievementUnlock>();
    if (Array.isArray(unlocked)) {
        for (const u of unlocked) {
            map.set(u.achievementId, u);
        }
    }
    return map;
  }, [unlocked]);

  const achievementsByCategory = useMemo((): Record<string, Achievement[]> => {
    const categories: Record<string, Achievement[]> = {};
    for (const ach of ACHIEVEMENTS_LIST) {
        if (!categories[ach.category]) {
            categories[ach.category] = [];
        }
        categories[ach.category].push(ach);
    }
    return categories;
  }, []);

  const totalAchievements = ACHIEVEMENTS_LIST.length;
  const unlockedCount = unlockedIds.size;

  return (
    <div className="animate-fade-in space-y-8 tab-bar-safe-area">
      <div>
        <h1 className="text-4xl font-bold uppercase tracking-wider">Logros</h1>
        <p className="text-slate-400 mt-2">Tu muro de trofeos. ¡Has desbloqueado {unlockedCount} de {totalAchievements} logros!</p>
      </div>

      <div className="w-full bg-slate-700 rounded-full h-2.5">
          <div 
              className="bg-primary-gradient h-2.5 rounded-full" 
              style={{ width: `${(unlockedCount / totalAchievements) * 100}%`, transition: 'width 0.5s ease-in-out' }}
          ></div>
      </div>

      <div className="space-y-6">
        {Object.entries(achievementsByCategory).map(([category, achievements]: [string, Achievement[]]) => (
            <div key={category}>
                <h2 className="text-2xl font-bold text-primary-color mb-4">{category}</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {Array.isArray(achievements) && achievements.map((achievement, index) => {
                      const isUnlocked = unlockedIds.has(achievement.id);
                      const IconComponent = (Icons as any)[achievement.icon] || Icons.TrophyIcon;

                      return (
                        <Card 
                            key={achievement.id} 
                            className={`transition-all duration-300 animate-subtle-fade-in-up ${isUnlocked ? 'border-yellow-400/50 bg-slate-800' : 'opacity-60 bg-slate-900'}`}
                            style={{ animationDelay: `${index * 50}ms` }}
                        >
                          <div className="flex items-center gap-4">
                            <div className={`p-3 rounded-full ${isUnlocked ? 'bg-yellow-400/20' : 'bg-slate-700'}`}>
                              <IconComponent size={24} className={isUnlocked ? 'text-yellow-400' : 'text-slate-500'} />
                            </div>
                            <div className="flex-1">
                              <h3 className={`font-bold ${isUnlocked ? 'text-yellow-300' : 'text-slate-300'}`}>{achievement.name}</h3>
                              <p className="text-sm text-slate-400">{achievement.description}</p>
                              {isUnlocked && (
                                <p className="text-xs text-slate-500 mt-1">
                                  Desbloqueado: {new Date(unlockedMap.get(achievement.id)!.date).toLocaleDateString()}
                                </p>
                              )}
                            </div>
                          </div>
                        </Card>
                      );
                    })}
                </div>
            </div>
        ))}
      </div>
    </div>
  );
};

export default AchievementsView;