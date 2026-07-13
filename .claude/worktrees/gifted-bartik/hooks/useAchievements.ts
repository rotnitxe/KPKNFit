// hooks/useAchievements.ts
import { useCallback } from 'react';
import useLocalStorage from './useLocalStorage';
import { WorkoutLog, AchievementUnlock, Achievement, Program } from '../types';
import { ACHIEVEMENTS_LIST } from '../data/achievements';

interface AchievementCheckContext {
    log?: WorkoutLog;
    history?: WorkoutLog[];
    programs?: Program[];
    programJustCreated?: boolean;
}

const useAchievements = () => {
    const [unlocked, setUnlocked] = useLocalStorage<AchievementUnlock[]>('yourprime-achievements', []);

    const checkAndUnlock = useCallback((context: AchievementCheckContext): Achievement[] => {
        const newlyUnlocked: Achievement[] = [];
        
        ACHIEVEMENTS_LIST.forEach(achievement => {
            // Check if already unlocked
            if (unlocked.some(u => u.achievementId === achievement.id)) return;
            
            // Perform the check
            if (achievement.check(context)) {
                newlyUnlocked.push(achievement);
            }
        });

        if (newlyUnlocked.length > 0) {
            const newUnlocks: AchievementUnlock[] = newlyUnlocked.map(ach => ({
                achievementId: ach.id, date: new Date().toISOString(),
            }));
            setUnlocked(prev => [...prev, ...newUnlocks]);
        }

        return newlyUnlocked;
    }, [unlocked, setUnlocked]);

    return { unlockedAchievements: unlocked, checkAndUnlock };
};

export default useAchievements;