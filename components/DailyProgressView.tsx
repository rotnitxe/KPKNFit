
// components/DailyProgressView.tsx
import React, { useMemo } from 'react';
import { NutritionLog, BodyProgressLog, Settings } from '../types';
import Card from './ui/Card';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend } from 'recharts';
import { UtensilsIcon, TrashIcon } from './icons';

const ProgressBar: React.FC<{ value: number; max: number; label: string; unit: string; color: string; }> = ({ value, max, label, unit, color }) => {
    const percentage = max > 0 ? (value / max) * 100 : 0;
    return (
        <div>
            <div className="flex justify-between items-baseline mb-1">
                <span className="text-sm font-semibold text-slate-200">{label}</span>
                <span className="text-xs text-slate-400">{Math.round(value)} / {Math.round(max)} {unit}</span>
            </div>
            <div className="w-full bg-slate-700 rounded-full h-2.5">
                <div className="h-2.5 rounded-full" style={{ width: `${Math.min(100, percentage)}%`, backgroundColor: color }}></div>
            </div>
        </div>
    );
};

const LoggedFoodList: React.FC<{ logs: NutritionLog[], setNutritionLogs: React.Dispatch<React.SetStateAction<NutritionLog[]>> }> = ({ logs, setNutritionLogs }) => {
    const groupedLogs = useMemo(() => {
        return logs.reduce((acc, log) => {
            const meal = log.mealType;
            if (!acc[meal]) {
                acc[meal] = [];
            }
            acc[meal].push(log);
            return acc;
        }, {} as Record<NutritionLog['mealType'], NutritionLog[]>);
    }, [logs]);

    const mealOrder: NutritionLog['mealType'][] = ['breakfast', 'lunch', 'dinner', 'snack'];
    const mealNames = { breakfast: 'Desayuno', lunch: 'Almuerzo', dinner: 'Cena', snack: 'Snack' };

    const handleDelete = (logId: string) => {
        if (window.confirm("¿Estás seguro de que quieres eliminar este registro?")) {
            setNutritionLogs(prev => prev.filter(log => log.id !== logId));
        }
    };

    return (
        <Card>
            <h3 className="text-xl font-bold mb-4 flex items-center gap-2"><UtensilsIcon /> Historial de Comidas de Hoy</h3>
            {logs.length === 0 ? (
                <p className="text-center text-sm text-slate-500 py-8">No has registrado ninguna comida hoy.</p>
            ) : (
                <div className="space-y-4">
                    {mealOrder.map(mealType => {
                        if (!groupedLogs[mealType]) return null;
                        return (
                            <div key={mealType}>
                                <h4 className="font-semibold text-primary-color mb-2">{mealNames[mealType]}</h4>
                                <ul className="space-y-2">
                                    {groupedLogs[mealType].map(log => (
                                        <li key={log.id} className="bg-slate-900/50 p-3 rounded-lg flex justify-between items-center text-sm group">
                                            <span className="flex-1 pr-2">{log.description}</span>
                                            <div className="text-right flex-shrink-0">
                                                <p className="font-bold text-white">{log.calories} kcal</p>
                                                <p className="text-xs text-slate-400">P: {log.protein}g</p>
                                            </div>
                                            <button onClick={() => handleDelete(log.id)} className="ml-2 p-1 text-slate-500 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <TrashIcon size={16} />
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )
                    })}
                </div>
            )}
        </Card>
    );
}

const DailyProgressView: React.FC<{
    nutritionLogs: NutritionLog[];
    bodyProgressLogs: BodyProgressLog[];
    settings: Settings;
    setNutritionLogs: React.Dispatch<React.SetStateAction<NutritionLog[]>>;
}> = ({ nutritionLogs, bodyProgressLogs, settings, setNutritionLogs }) => {
    const todayStr = new Date().toISOString().split('T')[0];
    const todaysLogs = useMemo(() => nutritionLogs.filter(log => log.date.startsWith(todayStr)), [nutritionLogs, todayStr]);

    const dailyTotals = useMemo(() => {
        return todaysLogs.reduce((acc, log) => {
            acc.calories += log.calories || 0;
            acc.protein += log.protein || 0;
            acc.carbs += log.carbs || 0;
            acc.fats += log.fats || 0;
            return acc;
        }, { calories: 0, protein: 0, carbs: 0, fats: 0 });
    }, [todaysLogs]);

    const macroData = useMemo(() => {
        const { protein, carbs, fats } = dailyTotals;
        const total = protein + carbs + fats;
        if (total === 0) return [];
        return [
            { name: 'Proteína', value: protein, color: '#3b82f6' },
            { name: 'Carbs', value: carbs, color: '#f97316' },
            { name: 'Grasas', value: fats, color: '#eab308' },
        ];
    }, [dailyTotals]);

    const progressPace = useMemo(() => {
        if (bodyProgressLogs.length < 2 || !settings.userVitals?.targetWeight) {
            return null;
        }

        const sortedLogs = [...bodyProgressLogs].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const startLog = sortedLogs[0];
        const currentLog = sortedLogs[sortedLogs.length - 1];
        
        const startWeight = startLog.weight;
        const currentWeight = currentLog.weight;
        const targetWeight = settings.userVitals.targetWeight;

        if (!startWeight || !currentWeight) return null;
        
        // Handle case where start and target are the same
        if (startWeight === targetWeight) return { percentage: currentWeight === targetWeight ? 100 : 0, weeksRemaining: null };

        const totalProgress = ((startWeight - currentWeight) / (startWeight - targetWeight)) * 100;

        const daysElapsed = (new Date(currentLog.date).getTime() - new Date(startLog.date).getTime()) / (1000 * 3600 * 24);
        if (daysElapsed < 1) return { percentage: totalProgress > 0 ? Math.min(100, totalProgress) : 0, weeksRemaining: null };
        
        const weeksElapsed = daysElapsed / 7;
        const totalWeightChange = startWeight - currentWeight;

        if (weeksElapsed < 1 || totalWeightChange === 0) {
            return { percentage: totalProgress > 0 ? Math.min(100, totalProgress) : 0, weeksRemaining: null };
        }

        const weeklyChange = totalWeightChange / weeksElapsed;
        const remainingWeight = currentWeight - targetWeight;
        
        let weeksRemaining: number | null = null;
        // Check if progress is being made towards the goal
        if ((weeklyChange > 0 && remainingWeight > 0) || (weeklyChange < 0 && remainingWeight < 0)) {
           weeksRemaining = Math.abs(Math.round(remainingWeight / weeklyChange));
        }

        return {
            percentage: totalProgress > 0 ? Math.min(100, totalProgress) : 0,
            weeksRemaining
        };

    }, [bodyProgressLogs, settings.userVitals?.targetWeight]);


    return (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <Card>
                <h3 className="text-xl font-bold mb-4">Consumo de Hoy</h3>
                {settings.dailyCalorieGoal ? (
                    <div className="space-y-4">
                        <ProgressBar value={dailyTotals.calories} max={settings.dailyCalorieGoal} label="Calorías" unit="kcal" color="#16a34a" />
                        <ProgressBar value={dailyTotals.protein} max={settings.dailyProteinGoal || 0} label="Proteína" unit="g" color="#3b82f6" />
                        <ProgressBar value={dailyTotals.carbs} max={settings.dailyCarbGoal || 0} label="Carbohidratos" unit="g" color="#f97316" />
                        <ProgressBar value={dailyTotals.fats} max={settings.dailyFatGoal || 0} label="Grasas" unit="g" color="#eab308" />
                    </div>
                ) : (
                    <p className="text-center text-sm text-slate-400 py-8">Configura tus objetivos en la pestaña "Mis Objetivos" para empezar el seguimiento.</p>
                )}
            </Card>

            <Card>
                 <h3 className="text-xl font-bold mb-4">Distribución de Macros</h3>
                 {macroData.length > 0 ? (
                    <ResponsiveContainer width="100%" height={200}>
                        <PieChart>
                            <Pie data={macroData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} label>
                                {macroData.map((entry) => <Cell key={`cell-${entry.name}`} fill={entry.color} />)}
                            </Pie>
                            <Legend />
                        </PieChart>
                    </ResponsiveContainer>
                 ) : (
                    <p className="text-center text-sm text-slate-400 py-8">Registra una comida para ver la distribución de tus macros.</p>
                 )}
            </Card>
            
            <div className="lg:col-span-2">
                <LoggedFoodList logs={todaysLogs} setNutritionLogs={setNutritionLogs} />
            </div>

            {progressPace && (
                <Card className="lg:col-span-2">
                    <h3 className="text-xl font-bold mb-4">Ritmo de Progreso</h3>
                     <div>
                        <ProgressBar 
                            value={progressPace.percentage}
                            max={100}
                            label="Progreso hacia tu meta"
                            unit="%"
                            color="var(--primary-color)"
                        />
                         {progressPace.weeksRemaining !== null && (
                             <p className="text-center text-sm text-slate-300 mt-4">
                                A este ritmo, alcanzarás tu meta en aproximadamente <span className="font-bold text-white">{progressPace.weeksRemaining} {progressPace.weeksRemaining === 1 ? 'semana' : 'semanas'}</span>.
                             </p>
                         )}
                    </div>
                </Card>
            )}
        </div>
    );
};

export default DailyProgressView;
