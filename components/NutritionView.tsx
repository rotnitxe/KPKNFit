
// components/NutritionView.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { NutritionLog, Settings, LoggedFood, ToastData } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { PlusIcon, UtensilsIcon, TrashIcon, SparklesIcon, BrainIcon, CheckCircleIcon, ClockIcon, TargetIcon, CalendarIcon, AlertTriangleIcon, InfoIcon, SaveIcon, PencilIcon, ActivityIcon, ArrowLeftIcon, ArrowDownIcon } from './icons';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import { generateFoodCategoryDescription } from '../services/aiService';

const ProgressBar: React.FC<{ value: number; max: number; label: string; unit: string; color: string; }> = ({ value, max, label, unit, color }) => {
    const percentage = max > 0 ? Math.min(100, (value / max) * 100) : 0;
    return (
        <div className="mb-3 last:mb-0">
            <div className="flex justify-between items-end mb-1.5">
                <span className="text-[10px] font-black text-slate-400 uppercase tracking-wider">{label}</span>
                <span className="text-xs font-bold text-slate-200">{Math.round(value)} <span className="text-slate-500">/ {Math.round(max)} {unit}</span></span>
            </div>
            <div className="w-full bg-slate-900 rounded-full h-2 relative overflow-hidden border border-white/5">
                <div className="h-full rounded-full transition-all duration-700 ease-out shadow-[0_0_10px_currentColor]" style={{ width: `${percentage}%`, backgroundColor: color }}></div>
            </div>
        </div>
    );
};

// Sub-component for listing logged foods
const LoggedFoodsList: React.FC<{ 
    logs: NutritionLog[], 
    setNutritionLogs: React.Dispatch<React.SetStateAction<NutritionLog[]>>,
    title: string,
    isPlanned?: boolean
}> = ({ logs, setNutritionLogs, title, isPlanned }) => {
    const { addToast } = useAppDispatch();
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
    const mealNames: Record<NutritionLog['mealType'], string> = { breakfast: 'Desayuno', lunch: 'Almuerzo', dinner: 'Cena', snack: 'Snack' };

    const handleDelete = (logId: string) => {
        if (window.confirm("¿Eliminar registro?")) {
            setNutritionLogs(prev => prev.filter(log => log.id !== logId));
            addToast("Registro eliminado.", "success");
        }
    };
    
    const handleConsume = (log: NutritionLog) => {
        setNutritionLogs(prev => prev.map(l => l.id === log.id ? { ...l, status: 'consumed' } : l));
        addToast("¡Comida registrada!", "success");
    };

    if (logs.length === 0) return null;

    return (
        <div className={`mb-6 ${isPlanned ? 'opacity-90' : ''}`}>
            <h3 className={`text-sm font-black uppercase tracking-widest mb-4 flex items-center gap-2 ${isPlanned ? 'text-purple-300' : 'text-slate-300'}`}>
                {isPlanned ? <ClockIcon size={14}/> : <UtensilsIcon size={14}/>} {title}
            </h3>
            <div className="space-y-4">
                {mealOrder.map(mealType => {
                    if (!groupedLogs[mealType]) return null;
                    
                    return (
                        <div key={mealType} className="bg-slate-900/30 border border-white/5 rounded-xl overflow-hidden">
                            <div className="px-4 py-2 bg-white/5 border-b border-white/5 flex justify-between items-center">
                                <h4 className="text-xs font-black text-slate-300 uppercase">{mealNames[mealType]}</h4>
                                {isPlanned && <span className="text-[9px] bg-purple-500/20 text-purple-300 px-2 py-0.5 rounded font-bold uppercase">Planificado</span>}
                            </div>
                            <div className="p-1">
                                {groupedLogs[mealType].map(log => {
                                    const totalCalories = log.foods.reduce((sum, food) => sum + food.calories, 0);
                                    const totalProtein = log.foods.reduce((sum, food) => sum + food.protein, 0);

                                    return (
                                        <div key={log.id} className="p-3 border-b border-white/5 last:border-0 hover:bg-white/5 transition-colors group rounded-lg">
                                            <div className="flex justify-between items-start">
                                                <div className="flex-1 pr-2">
                                                    <ul className="space-y-1">
                                                        {log.foods.map(food => (
                                                            <li key={food.id} className="text-sm text-slate-200 font-medium">
                                                                {food.foodName} <span className="text-slate-500 text-xs">({food.amount}{food.unit})</span>
                                                            </li>
                                                        ))}
                                                    </ul>
                                                    {log.notes && <p className="text-[10px] text-slate-500 italic mt-1">"{log.notes}"</p>}
                                                </div>
                                                <div className="text-right flex flex-col items-end">
                                                    <p className="font-mono font-bold text-white text-sm">{Math.round(totalCalories)} <span className="text-[10px] text-slate-500">kcal</span></p>
                                                    <p className="text-[10px] text-blue-400 font-bold">{totalProtein.toFixed(0)}g P</p>
                                                </div>
                                            </div>
                                            <div className="flex justify-end gap-3 mt-2 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
                                                {isPlanned && (
                                                    <button onClick={() => handleConsume(log)} className="text-green-400 hover:text-green-300 text-[10px] font-bold uppercase flex items-center gap-1 bg-green-900/20 px-2 py-1 rounded">
                                                        <CheckCircleIcon size={12} /> Completar
                                                    </button>
                                                )}
                                                <button onClick={() => handleDelete(log.id)} className="text-red-400 hover:text-red-300 text-[10px] font-bold uppercase flex items-center gap-1 bg-red-900/20 px-2 py-1 rounded">
                                                    <TrashIcon size={12} /> Borrar
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )
                })}
            </div>
        </div>
    );
}

// --- Mission Control Component (NEW & IMPROVED) ---
const MissionControlCard: React.FC = () => {
    const { settings } = useAppState();
    const { setSettings, addToast, navigateTo } = useAppDispatch();
    
    // Local state for editing
    const [isEditing, setIsEditing] = useState(false);
    const [targetWeight, setTargetWeight] = useState<string>(settings.userVitals.targetWeight?.toString() || '');
    const [targetDate, setTargetDate] = useState<string>(settings.userVitals.targetDate || '');

    // Viability Calculation Logic (Step 4.3)
    const viability = useMemo(() => {
        const currentWeight = settings.userVitals.weight;
        const tWeight = parseFloat(targetWeight);
        
        if (!currentWeight || isNaN(tWeight) || !targetDate) return null;
        
        const now = new Date();
        const deadline = new Date(targetDate);
        const diffTime = deadline.getTime() - now.getTime();
        const diffWeeks = Math.ceil(diffTime / (1000 * 60 * 60 * 24 * 7));
        
        // Handle dates in the past
        if (diffWeeks <= 0) {
            return { status: 'expired', msg: 'La fecha límite ya ha pasado o es hoy.', color: 'text-red-400', bg: 'bg-red-900/20', border: 'border-red-500/30', icon: AlertTriangleIcon, title: 'Fecha Vencida' };
        }

        const weightDiff = currentWeight - tWeight; // Positive = Lose weight, Negative = Gain weight
        const isLoss = weightDiff > 0;
        const requiredWeeklyRate = Math.abs(weightDiff) / diffWeeks;
        const percentageOfBodyweight = (requiredWeeklyRate / currentWeight) * 100;
        
        let status: 'safe' | 'aggressive' | 'dangerous' = 'safe';
        
        // Viability Thresholds (1% per week is generally max recommended for sustainable loss)
        if (percentageOfBodyweight > 1.5) status = 'dangerous';
        else if (percentageOfBodyweight > 0.8) status = 'aggressive';
        
        // Gain logic (Muscle gain is slower than fat loss)
        if (!isLoss) {
             if (percentageOfBodyweight > 0.5) status = 'dangerous'; // Gaining >0.5% week is likely mostly fat
             else if (percentageOfBodyweight > 0.25) status = 'aggressive';
        }
        
        const config = {
            safe: { 
                color: 'text-emerald-400', 
                bg: 'bg-emerald-900/20', 
                border: 'border-emerald-500/30',
                icon: CheckCircleIcon, 
                title: 'Objetivo Sostenible' 
            },
            aggressive: { 
                color: 'text-yellow-400', 
                bg: 'bg-yellow-900/20', 
                border: 'border-yellow-500/30',
                icon: InfoIcon, 
                title: 'Objetivo Agresivo' 
            },
            dangerous: { 
                color: 'text-red-400', 
                bg: 'bg-red-900/20', 
                border: 'border-red-500/30',
                icon: AlertTriangleIcon, 
                title: isLoss ? 'Objetivo Poco Realista' : 'Ganancia Probable de Grasa' 
            }
        };

        return {
            status,
            weeks: diffWeeks,
            rate: requiredWeeklyRate.toFixed(2),
            totalDiff: Math.abs(weightDiff).toFixed(1),
            isLoss,
            ...config[status]
        };
    }, [settings.userVitals.weight, targetWeight, targetDate]);

    const handleSave = () => {
        const numWeight = parseFloat(targetWeight);
        if (isNaN(numWeight)) return;

        const newVitals = { 
            ...settings.userVitals, 
            targetWeight: numWeight, 
            targetDate: targetDate 
        };

        // If defining a new mission (or resetting), snapshot start point
        if (!settings.userVitals.targetStartDate || !settings.userVitals.targetWeight) {
            newVitals.targetStartDate = new Date().toISOString().split('T')[0];
            newVitals.targetStartWeight = settings.userVitals.weight;
        }

        setSettings({ userVitals: newVitals });
        setIsEditing(false);
        addToast("Misión actualizada.", "success");
    };

    if (!settings.userVitals.weight) {
         return (
             <Card onClick={() => navigateTo('athlete-profile')} className="cursor-pointer border-dashed border-2 border-slate-700 hover:border-slate-500 transition-colors">
                 <div className="text-center py-4">
                     <TargetIcon className="mx-auto text-slate-500 mb-2"/>
                     <p className="text-sm text-slate-400">Registra tu peso actual en tu Perfil de Atleta para habilitar la Misión.</p>
                     <p className="text-xs text-primary-color font-bold mt-2">Ir a Perfil →</p>
                 </div>
             </Card>
         );
    }

    if (!settings.userVitals.targetWeight && !isEditing) {
        return (
            <Card className="border-dashed border-2 border-slate-700 hover:border-primary-color transition-colors group cursor-pointer" onClick={() => setIsEditing(true)}>
                <div className="text-center py-6">
                    <TargetIcon className="mx-auto text-primary-color mb-3 group-hover:scale-110 transition-transform"/>
                    <h3 className="font-bold text-white text-lg">Definir Nueva Misión</h3>
                    <p className="text-xs text-slate-400 mt-1">Establece una meta de peso y fecha límite.</p>
                </div>
            </Card>
        );
    }

    return (
        <Card className="bg-gradient-to-br from-slate-900 to-black border-l-4 border-l-primary-color relative overflow-hidden">
            {/* Background Icon */}
            <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none"><TargetIcon size={120} /></div>

            <div className="flex justify-between items-start mb-4 relative z-10">
                <h3 className="text-lg font-black text-white uppercase tracking-tighter flex items-center gap-2">
                    <TargetIcon className="text-primary-color" size={18}/> Tu Misión
                </h3>
                <button onClick={() => setIsEditing(!isEditing)} className="text-slate-400 hover:text-white p-1 rounded-md hover:bg-white/5 transition-colors">
                    <PencilIcon size={14}/>
                </button>
            </div>

            {isEditing ? (
                <div className="space-y-4 animate-fade-in relative z-10">
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="text-[10px] font-bold text-slate-500 uppercase block mb-1">Peso Meta ({settings.weightUnit})</label>
                            <input 
                                type="number" 
                                step="0.1"
                                value={targetWeight} 
                                onChange={e => setTargetWeight(e.target.value)} 
                                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-3 text-white font-black text-lg focus:border-primary-color outline-none"
                                placeholder="0.0"
                            />
                        </div>
                        <div>
                            <label className="text-[10px] font-bold text-slate-500 uppercase block mb-1">Fecha Límite</label>
                            <input 
                                type="date" 
                                value={targetDate} 
                                onChange={e => setTargetDate(e.target.value)} 
                                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-3.5 text-white font-bold text-xs focus:border-primary-color outline-none transition-all"
                            />
                        </div>
                    </div>
                    
                    {/* Feedback Viabilidad (Paso 4.3) */}
                    {viability && (
                        <div className={`p-3 rounded-xl border ${viability.bg} ${viability.border} animate-fade-in`}>
                            <div className="flex items-start gap-3">
                                <div className={`p-2 rounded-full bg-black/20 ${viability.color}`}>
                                    <viability.icon size={16} />
                                </div>
                                <div>
                                    <h4 className={`text-xs font-bold ${viability.color} mb-1`}>{viability.title}</h4>
                                    <p className="text-[10px] text-slate-300 leading-tight">
                                        {viability.status === 'expired' 
                                            ? viability.msg 
                                            : `Para llegar a la meta, necesitas ${viability.isLoss ? 'perder' : 'ganar'} ${viability.rate} ${settings.weightUnit}/semana durante ${viability.weeks} semanas.`}
                                    </p>
                                </div>
                            </div>
                        </div>
                    )}
                    
                    <div className="flex gap-2 pt-2">
                         <Button onClick={() => setIsEditing(false)} variant="secondary" className="flex-1 !text-xs uppercase font-bold">Cancelar</Button>
                         <Button onClick={handleSave} className="flex-[2] !py-2 !text-xs uppercase font-black"><SaveIcon size={14} className="mr-1"/> Guardar Misión</Button>
                    </div>
                </div>
            ) : (
                <div className="relative z-10">
                    <div className="flex items-center justify-between mb-4">
                        <div className="text-center">
                            <p className="text-[10px] text-slate-500 uppercase font-bold tracking-widest">Inicio</p>
                            <p className="text-sm font-bold text-slate-300">{settings.userVitals.targetStartWeight || '--'}</p>
                        </div>
                        
                        <div className="flex-1 px-4 text-center">
                             <div className="text-3xl font-black text-white leading-none">{settings.userVitals.weight} <span className="text-xs text-slate-500 font-bold ml-1">{settings.weightUnit}</span></div>
                             <div className="w-full bg-slate-800 h-2 rounded-full mt-2 relative overflow-hidden">
                                 {/* Progress Bar Logic can be refined based on start/current/target relation */}
                                 <div className="absolute inset-0 bg-primary-color opacity-30 w-1/2" /> 
                             </div>
                             <p className="text-[9px] text-slate-400 mt-2 uppercase font-bold tracking-widest flex items-center justify-center gap-1">
                                 <ClockIcon size={10}/> {viability?.weeks || 0} SEMANAS RESTANTES
                             </p>
                        </div>

                        <div className="text-center">
                            <p className="text-[10px] text-slate-500 uppercase font-bold tracking-widest">Meta</p>
                            <p className="text-xl font-bold text-primary-color">{settings.userVitals.targetWeight}</p>
                        </div>
                    </div>
                    
                    {/* Summary Text */}
                    {viability && viability.status !== 'expired' && (
                        <div className="bg-white/5 rounded-lg p-2 text-center border border-white/5">
                             <p className="text-[10px] text-slate-300">
                                 Ritmo actual requerido: <strong className={viability.color}>{viability.rate} {settings.weightUnit}/sem</strong>
                             </p>
                        </div>
                    )}
                </div>
            )}
        </Card>
    );
};

// Main View Component
const NutritionView: React.FC = () => {
  const { nutritionLogs, settings } = useAppState();
  const { setIsNutritionLogModalOpen, navigateTo, setNutritionLogs } = useAppDispatch();
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);

  // Filtrar logs por la fecha seleccionada
  const logsForDate = useMemo(() => {
      return nutritionLogs.filter(log => log.date && log.date.startsWith(selectedDate));
  }, [nutritionLogs, selectedDate]);

  const consumedLogs = useMemo(() => logsForDate.filter(l => l.status === 'consumed' || !l.status), [logsForDate]);
  const plannedLogs = useMemo(() => logsForDate.filter(l => l.status === 'planned'), [logsForDate]);

  const dailyTotals = useMemo(() => {
      return consumedLogs.reduce((acc, log) => {
          log.foods.forEach(food => {
              acc.calories += food.calories || 0;
              acc.protein += food.protein || 0;
              acc.carbs += food.carbs || 0;
              acc.fats += food.fats || 0;
          });
          return acc;
      }, { calories: 0, protein: 0, carbs: 0, fats: 0 });
  }, [consumedLogs]);

  const hasGoals = settings.dailyCalorieGoal && settings.dailyCalorieGoal > 0;

  return (
    <div className="space-y-6 pb-24">
      <header className="flex justify-between items-center pt-4 mb-2">
          <h1 className="text-3xl font-black uppercase tracking-tighter text-white">Nutrición</h1>
          <Button onClick={() => setIsNutritionLogModalOpen(true)} className="!py-2 !px-3 !text-xs !font-black uppercase shadow-lg shadow-primary-color/20"><PlusIcon size={14} className="mr-1"/> Registrar</Button>
      </header>

      {/* Date Picker & Planner */}
      <div className="bg-slate-900/50 border border-white/5 rounded-xl p-3 flex justify-between items-center">
         <div className="flex items-center gap-2">
             <CalendarIcon size={16} className="text-slate-400"/>
             <input
                type="date"
                value={selectedDate}
                onChange={e => setSelectedDate(e.target.value)}
                className="bg-transparent border-none text-white text-sm font-bold focus:ring-0 p-0"
            />
         </div>
         <Button onClick={() => navigateTo('smart-meal-planner')} variant="secondary" className="!py-1 !px-3 !text-[10px] uppercase font-black">
            <SparklesIcon size={12} className="mr-1"/> Planificador
        </Button>
      </div>

      {/* Mission Control Card */}
      <MissionControlCard />
      
        {/* Main Dashboard Card (Updates with selectedDate) */}
        <div className="bg-gradient-to-br from-slate-900 to-black border border-white/10 rounded-2xl p-5 shadow-2xl relative overflow-hidden">
             <div className="absolute top-0 right-0 w-32 h-32 bg-primary-color/10 blur-[50px] rounded-full pointer-events-none"></div>

            {hasGoals ? (
                 <div className="space-y-6 relative z-10">
                    <div className="flex justify-between items-end border-b border-white/5 pb-4">
                        <div>
                             <p className="text-[10px] uppercase font-black text-slate-500 tracking-widest mb-1">Calorías ({new Date(selectedDate).toLocaleDateString()})</p>
                             <div className="flex items-baseline gap-1">
                                <span className="text-4xl font-black text-white">{Math.round(dailyTotals.calories)}</span>
                                <span className="text-sm font-bold text-slate-500">/ {Math.round(settings.dailyCalorieGoal!)}</span>
                             </div>
                        </div>
                        <div className="text-right">
                             <span className={`text-xs font-bold px-2 py-1 rounded ${settings.dailyCalorieGoal! - dailyTotals.calories < 0 ? 'bg-red-500/10 text-red-400' : 'bg-green-500/10 text-green-400'}`}>
                                 {Math.round(settings.dailyCalorieGoal! - dailyTotals.calories)} kcal rest
                             </span>
                        </div>
                    </div>
                    
                    <div className="space-y-2">
                        <ProgressBar value={dailyTotals.protein} max={settings.dailyProteinGoal || 0} label="Proteína" unit="g" color="#3b82f6" />
                        <ProgressBar value={dailyTotals.carbs} max={settings.dailyCarbGoal || 0} label="Carbohidratos" unit="g" color="#f97316" />
                        <ProgressBar value={dailyTotals.fats} max={settings.dailyFatGoal || 0} label="Grasas" unit="g" color="#eab308" />
                    </div>
                </div>
            ) : (
                <div className="text-center py-6">
                    <p className="text-sm text-slate-400 font-medium mb-3">
                        Define tus macros en el Perfil de Atleta para ver tu progreso.
                    </p>
                    <Button variant="secondary" className="!text-xs uppercase font-black" onClick={() => navigateTo('athlete-profile')}>Ir a Perfil</Button>
                </div>
            )}
        </div>
        
        {plannedLogs.length > 0 && (
            <LoggedFoodsList logs={plannedLogs} setNutritionLogs={setNutritionLogs} title="Planificado" isPlanned={true} />
        )}

        {consumedLogs.length > 0 ? (
             <LoggedFoodsList logs={consumedLogs} setNutritionLogs={setNutritionLogs} title="Consumido" />
        ) : (
            plannedLogs.length === 0 && (
                <div className="text-center py-10 opacity-50 bg-slate-900/30 rounded-2xl border-dashed border border-white/5">
                    <UtensilsIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                    <p className="text-xs text-slate-500 font-bold uppercase tracking-widest">Sin registros para esta fecha</p>
                </div>
            )
        )}

    </div>
  );
};

export default NutritionView;
