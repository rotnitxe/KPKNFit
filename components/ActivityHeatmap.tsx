
// components/ActivityHeatmap.tsx
import React, { useMemo, useRef, useEffect } from 'react';
import { WorkoutLog, Settings } from '../types';
import Card from './ui/Card';

interface ActivityHeatmapProps {
  history: WorkoutLog[];
  settings: Settings;
}

const ActivityCalendar: React.FC<ActivityHeatmapProps> = ({ history, settings }) => {
    const scrollRef = useRef<HTMLDivElement>(null);

    const { days, months } = useMemo(() => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        
        const dayCount = 180; // Show last ~6 months
        const startDate = new Date(today);
        startDate.setDate(today.getDate() - dayCount);

        const activityByDate: Record<string, number> = {};
        let maxVolume = 0;

        history.forEach(log => {
            const date = new Date(log.date);
            date.setHours(0, 0, 0, 0);
            const dateStr = date.toISOString().split('T')[0];
            // Ensure completedExercises exists
            const exercises = log.completedExercises || [];
            const volume = exercises.reduce((total, ex) =>
                total + ex.sets.reduce((setTotal, s) => setTotal + (s.weight || 0) * (s.completedReps || 0), 0),
            0);
            activityByDate[dateStr] = (activityByDate[dateStr] || 0) + volume;
            if (activityByDate[dateStr] > maxVolume) {
                maxVolume = activityByDate[dateStr];
            }
        });

        const days = [];
        const monthPositions = [];
        let lastMonth = -1;

        for (let i = 0; i <= dayCount; i++) {
            const currentDate = new Date(startDate);
            currentDate.setDate(startDate.getDate() + i);
            const dateStr = currentDate.toISOString().split('T')[0];
            const volume = activityByDate[dateStr] || 0;
            
            let level = 0;
            if (volume > 0) {
                const percentage = volume / (maxVolume || 1);
                if (percentage > 0.75) level = 4;
                else if (percentage > 0.5) level = 3;
                else if (percentage > 0.25) level = 2;
                else level = 1;
            }

            days.push({
                date: currentDate,
                level: level,
                isToday: currentDate.getTime() === today.getTime(),
            });
            
            const currentMonth = currentDate.getMonth();
            if (currentMonth !== lastMonth) {
                monthPositions.push({
                    month: currentDate.toLocaleString('es-ES', { month: 'short' }),
                    startColumn: i + 1,
                });
                lastMonth = currentMonth;
            }
        }
        
        return { days, months: monthPositions };
    }, [history]);
    
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
        }
    }, []);

    const weekDays = ['D', 'L', 'M', 'X', 'J', 'V', 'S'];

    return (
        <Card className="md:col-span-3">
             <h3 className="text-xl font-bold mb-4">Actividad Reciente</h3>
            <div className="relative">
                <div className="grid grid-flow-col" style={{gridTemplateColumns: `20px repeat(${months.length}, auto)`, gridTemplateRows: '20px'}}>
                     <div className="row-start-1 col-start-1" />
                     {months.map((month, index) => (
                        <div key={index} className="text-xs text-slate-400 col-start-2 overflow-visible whitespace-nowrap">
                           {month.month.charAt(0).toUpperCase() + month.month.slice(1)}
                        </div>
                     ))}
                </div>

                <div className="flex gap-2">
                    <div className="grid grid-rows-7 gap-1 text-xs text-slate-500 shrink-0">
                        {weekDays.map((day, i) => ( i % 2 !== 0 && <div key={day} className="h-[14px] flex items-center">{day}</div> ))}
                    </div>
                    <div ref={scrollRef} className="heatmap-scroll-container hide-scrollbar w-full">
                        <div className="heatmap-grid" style={{ gridTemplateColumns: `repeat(${days.length}, 14px)`, gap: '2px' }}>
                            {days.map((day, i) => (
                                <div
                                    key={i}
                                    className={`heatmap-cell w-[14px] h-[14px] rounded-sm ${day.isToday ? 'border-2 border-white' : ''}`}
                                    data-level={day.level}
                                    style={{ gridRowStart: day.date.getDay() + 1 }}
                                    title={day.date.toLocaleDateString('es-ES')}
                                />
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </Card>
    );
};

export default ActivityCalendar;