
// components/AddNutritionLogModal.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { NutritionLog, Settings, LoggedFood, PantryItem } from '../types';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { PlusIcon, TrashIcon, SearchIcon } from './icons';
import { FoodSearchModal } from './FoodSearchModal';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { queueFatigueDataPoint } from '../services/augeAdaptiveService';

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return new Date(dateString + 'T12:00:00Z').toISOString();
    }
    return new Date().toISOString();
};

interface AddNutritionLogModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: NutritionLog) => void;
  isOnline: boolean;
  settings: Settings;
  initialDate?: string;
}

const AddNutritionLogModal: React.FC<AddNutritionLogModalProps> = ({ isOpen, onClose, onSave, isOnline, settings, initialDate }) => {
  const { pantryItems } = useAppState();
  const { addToast } = useAppDispatch();
  const [mealType, setMealType] = useState<NutritionLog['mealType']>('lunch');
  const [logDate, setLogDate] = useState(initialDate || new Date().toISOString().split('T')[0]);
  const [foods, setFoods] = useState<LoggedFood[]>([]);
  const [notes, setNotes] = useState('');
  
  // Flattened UI State
  const [isAddingMode, setIsAddingMode] = useState(true); // Always visible add mode initially if empty

  useEffect(() => {
    if (isOpen) {
        setMealType('lunch');
        setLogDate(initialDate || new Date().toISOString().split('T')[0]);
        setFoods([]);
        setNotes('');
        setIsAddingMode(true);
    }
  }, [isOpen, initialDate]);

  useEffect(() => {
      // If foods added, maybe collapse add mode to show list better?
      // For now, keep it manual.
  }, [foods]);

  const addFoodToList = (food: Omit<LoggedFood, 'id'>) => {
    setFoods(prev => [...prev, { ...food, id: crypto.randomUUID() }]);
    addToast(`${food.foodName} añadido`, 'success');
  };

  const removeFoodFromList = (id: string) => {
    setFoods(prev => prev.filter(f => f.id !== id));
  };

  const handleSave = () => {
    if (foods.length === 0) {
      addToast("Añade al menos un alimento.", "danger");
      return;
    }
    const newLog: NutritionLog = {
      id: crypto.randomUUID(),
      date: safeCreateISOStringFromDateInput(logDate),
      mealType,
      foods,
      notes,
      status: 'consumed'
    };
    onSave(newLog);
    addToast("Comida registrada.", "success");

    const dailyGoal = settings.dailyCalorieGoal || 2500;
    const mealCals = totalMacros.calories;
    const runningRatio = mealCals / (dailyGoal / 4);

    if (runningRatio < 0.6) {
        setTimeout(() => addToast("Déficit detectado — AUGE penalizará recuperación muscular +35%", "suggestion"), 800);
    } else if (runningRatio > 1.3) {
        setTimeout(() => addToast("Superávit detectado — AUGE acelera recuperación muscular -15%", "success"), 800);
    } else {
        setTimeout(() => addToast("AUGE ajustará tu tasa de recuperación con este registro.", "suggestion"), 800);
    }

    const nutritionStatus = runningRatio < 0.7 ? -1 : runningRatio > 1.1 ? 1 : 0;
    queueFatigueDataPoint({
        hours_since_session: 0,
        session_stress: 0,
        sleep_hours: 7,
        nutrition_status: nutritionStatus,
        stress_level: 3,
        age: settings.age || 25,
        is_compound_dominant: false,
        observed_fatigue_fraction: 0,
    });

    onClose();
  };

  const totalMacros = useMemo(() => {
    return foods.reduce((acc, food) => {
        acc.calories += food.calories;
        acc.protein += food.protein;
        return acc;
    }, { calories: 0, protein: 0 });
  }, [foods]);

  const mealOptions: {id: NutritionLog['mealType'], label: string}[] = [
      { id: 'breakfast', label: 'Desayuno' },
      { id: 'lunch', label: 'Almuerzo' },
      { id: 'dinner', label: 'Cena' },
      { id: 'snack', label: 'Snack' }
  ];

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="">
      <div className="flex flex-col h-full space-y-6">
        
        {/* --- Header: Date & Meal Type Selection (Compact) --- */}
        <div className="flex justify-between items-center">
             <input 
                type="date" 
                value={logDate} 
                onChange={e => setLogDate(e.target.value)} 
                className="bg-transparent text-slate-400 text-xs font-medium border-none outline-none p-0 focus:ring-0 w-auto"
            />
            <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                {mealOptions.map(opt => (
                    <button 
                        key={opt.id}
                        onClick={() => setMealType(opt.id)}
                        className={`px-3 py-1 rounded-md text-[10px] font-bold uppercase transition-all ${mealType === opt.id ? 'bg-white text-black shadow-sm' : 'text-slate-500 hover:text-slate-300'}`}
                    >
                        {opt.label.substring(0, 3)}
                    </button>
                ))}
            </div>
        </div>

        {/* --- Total Summary (Elegant Number) --- */}
        <div className="text-center py-2">
            <span className="text-5xl font-black text-white tracking-tighter">{Math.round(totalMacros.calories)}</span>
            <span className="text-sm font-bold text-slate-500 ml-1">kcal</span>
            <div className="text-xs font-semibold text-blue-400 mt-1">{totalMacros.protein.toFixed(1)}g Proteína</div>
        </div>

        {/* --- Food List (Clean) --- */}
        <div className="space-y-0">
             {foods.length === 0 && !isAddingMode && (
                 <p className="text-center text-sm text-slate-600 italic py-4">Tu plato está vacío.</p>
             )}
             {foods.map((food, idx) => (
                 <div key={food.id} className="flex justify-between items-center py-3 border-b border-white/5 last:border-0 group">
                     <div>
                         <p className="font-bold text-sm text-slate-200">{food.foodName}</p>
                         <p className="text-xs text-slate-500">{food.amount}{food.unit} • {Math.round(food.calories)} kcal</p>
                     </div>
                     <button onClick={() => removeFoodFromList(food.id)} className="text-slate-600 hover:text-red-400 transition-colors p-2">
                         <TrashIcon size={14}/>
                     </button>
                 </div>
             ))}
        </div>

        {/* --- Add Interface (Integrated) --- */}
        <div className="bg-white/5 rounded-2xl p-4 overflow-hidden">
             <AddFoodComponent 
                onAddFood={addFoodToList} 
                pantryItems={pantryItems} 
             />
        </div>

        {/* --- Note Input (Subtle) --- */}
        <input 
            value={notes} 
            onChange={e => setNotes(e.target.value)} 
            placeholder="Añadir nota..." 
            className="w-full bg-transparent border-b border-white/10 py-3 text-xs text-slate-400 focus:border-white/30 focus:outline-none placeholder-slate-700"
        />

        {/* --- Footer Action --- */}
        <div className="pt-4">
            <Button 
                onClick={handleSave} 
                className={`w-full !py-4 !rounded-xl !text-sm shadow-xl border-none font-bold transition-all
                    ${foods.length > 0 ? 'bg-white text-black hover:bg-slate-200 opacity-100' : 'bg-slate-800 text-slate-500 cursor-not-allowed opacity-50'}
                `}
                disabled={foods.length === 0}
            >
                {foods.length > 0 ? `Confirmar Comida (${Math.round(totalMacros.calories)} kcal)` : 'Añade alimentos para guardar'}
            </Button>
        </div>

      </div>
    </Modal>
  );
};

// --- Compact Add Food Component (FoodSearchModal + Despensa) ---
const AddFoodComponent: React.FC<{
  onAddFood: (food: Omit<LoggedFood, 'id'>) => void;
  pantryItems: PantryItem[];
}> = ({ onAddFood, pantryItems }) => {
  const { addToast } = useAppDispatch();
  const [mode, setMode] = useState<'search' | 'pantry'>('search');
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);

  const handleSearchSelect = (logged: LoggedFood) => {
    onAddFood(logged);
    addToast(`${logged.foodName} añadido`, 'success');
    setIsSearchModalOpen(false);
  };

  return (
    <div className="flex flex-col gap-4">
        <div className="flex gap-6 border-b border-white/5 text-[10px] font-bold uppercase tracking-wider text-slate-500 pb-2">
            <button onClick={() => setMode('search')} className={`pb-1 transition-colors ${mode === 'search' ? 'text-white border-b-2 border-white' : 'hover:text-slate-300'}`}>Buscar</button>
            <button onClick={() => setMode('pantry')} className={`pb-1 transition-colors ${mode === 'pantry' ? 'text-emerald-400 border-b-2 border-emerald-400' : 'hover:text-slate-300'}`}>Despensa</button>
        </div>

        <div className="relative flex items-center">
            {mode === 'pantry' ? (
                 <select onChange={(e) => {
                     const item = pantryItems.find(p => p.id === e.target.value);
                     if(item) {
                         onAddFood({ foodName: item.name, amount: 100, unit: item.unit, calories: item.calories, protein: item.protein, carbs: item.carbs, fats: item.fats });
                         addToast(`${item.name} añadido`, 'success');
                     }
                 }} className="w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-sm text-white outline-none">
                    <option value="">Selecciona de la despensa...</option>
                    {pantryItems.map(p => <option key={p.id} value={p.id} className="text-black">{p.name}</option>)}
                 </select>
            ) : (
                <button
                    onClick={() => setIsSearchModalOpen(true)}
                    className="w-full flex items-center gap-3 bg-slate-900 border border-slate-700 rounded-xl p-3 text-left text-slate-400 hover:text-white hover:border-slate-600 transition-colors"
                >
                    <SearchIcon size={18} className="text-slate-500 shrink-0" />
                    <span>Buscar alimento (USDA, Open Food Facts...)</span>
                </button>
            )}
        </div>

        <FoodSearchModal
            isOpen={isSearchModalOpen}
            onClose={() => setIsSearchModalOpen(false)}
            onSelect={handleSearchSelect}
        />
    </div>
  );
};

export default AddNutritionLogModal;
