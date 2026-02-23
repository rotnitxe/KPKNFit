import React from 'react';
import { UtensilsIcon } from '../icons';

interface NutritionHeroBannerProps {
    selectedDate: string;
    onDateChange: (date: string) => void;
    dailyCalories: number;
    calorieGoal: number;
    hasCalorieGoal: boolean;
}

const NutritionHeroBanner: React.FC<NutritionHeroBannerProps> = ({
    selectedDate, onDateChange, dailyCalories, calorieGoal, hasCalorieGoal,
}) => {
    const pct = hasCalorieGoal && calorieGoal > 0 ? Math.min(100, (dailyCalories / calorieGoal) * 100) : 0;
    const statusColor = pct > 110 ? '#ef4444' : pct < 90 ? '#22c55e' : '#eab308';

    return (
        <div className="relative w-full shrink-0 min-h-[120px] sm:min-h-[130px]">
            <div className="absolute inset-0 bg-gradient-to-r from-[#0a0a0a] to-[#111]" />
            <div className="absolute inset-0 bg-gradient-to-r from-black/80 via-black/60 to-black/80" />

            <div className="relative z-10 h-full flex flex-col gap-3 px-4 py-3">
                <div className="flex items-center gap-3 shrink-0">
                    <div className="w-10 h-10 rounded-xl bg-[#FF7B00]/20 flex items-center justify-center shrink-0">
                        <UtensilsIcon size={20} className="text-[#FF7B00]" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-lg font-black text-white uppercase tracking-tight leading-tight">
                            Nutrición
                        </h1>
                        <input
                            type="date"
                            value={selectedDate}
                            onChange={e => onDateChange(e.target.value)}
                            className="bg-transparent border-none text-[#8E8E93] text-xs font-bold mt-0.5 focus:ring-0 p-0 cursor-pointer"
                        />
                    </div>
                </div>

                <div className="flex items-center gap-6 flex-wrap shrink-0">
                    <div className="flex items-center gap-1.5">
                        <span className="text-xl font-black text-white leading-none">{Math.round(dailyCalories)}</span>
                        <span className="text-[10px] text-[#48484A] font-bold">kcal</span>
                    </div>
                    {hasCalorieGoal && (
                        <>
                            <div className="w-px h-5 bg-white/10 hidden sm:block" />
                            <div className="flex items-center gap-1.5">
                                <span className="text-xl font-black leading-none" style={{ color: statusColor }}>{Math.round(pct)}%</span>
                                <span className="text-[10px] text-[#48484A] font-bold">objetivo</span>
                            </div>
                            <div className="w-px h-5 bg-white/10 hidden sm:block" />
                            <div className="flex items-center gap-1.5">
                                <span className="text-[10px] text-[#48484A] font-bold">meta {Math.round(calorieGoal)} kcal</span>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default NutritionHeroBanner;
