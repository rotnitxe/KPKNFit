import React, { useEffect, useMemo, useState } from 'react';
import { NutritionLog, Settings, LoggedFood } from '../types';
import { TacticalModal } from './ui/TacticalOverlays';
import Button from './ui/Button';
import { SearchIcon, TrashIcon } from './icons';
import { FoodSearchModal } from './FoodSearchModal';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { queueFatigueDataPoint } from '../services/augeAdaptiveService';
import { getLocalDateString, dateStringToISOString } from '../utils/dateUtils';

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return dateStringToISOString(dateString);
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

const mealOptions: { id: NutritionLog['mealType']; label: string }[] = [
    { id: 'breakfast', label: 'Desayuno' },
    { id: 'lunch', label: 'Almuerzo' },
    { id: 'dinner', label: 'Cena' },
    { id: 'snack', label: 'Snack' },
];

const AddNutritionLogModal: React.FC<AddNutritionLogModalProps> = ({ isOpen, onClose, onSave, settings, initialDate }) => {
    const { pantryItems } = useAppState();
    const { addToast } = useAppDispatch();

    const [mealType, setMealType] = useState<NutritionLog['mealType']>('lunch');
    const [logDate, setLogDate] = useState(initialDate || getLocalDateString());
    const [foods, setFoods] = useState<LoggedFood[]>([]);
    const [notes, setNotes] = useState('');

    const [mode, setMode] = useState<'search' | 'pantry'>('search');
    const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);

    useEffect(() => {
        if (!isOpen) return;
        setMealType('lunch');
        setLogDate(initialDate || getLocalDateString());
        setFoods([]);
        setNotes('');
        setMode('search');
    }, [isOpen, initialDate]);

    const totalMacros = useMemo(() => {
        return foods.reduce(
            (acc, food) => {
                acc.calories += food.calories || 0;
                acc.protein += food.protein || 0;
                acc.carbs += food.carbs || 0;
                acc.fats += food.fats || 0;
                return acc;
            },
            { calories: 0, protein: 0, carbs: 0, fats: 0 }
        );
    }, [foods]);

    const addFoodToList = (food: Omit<LoggedFood, 'id'>) => {
        setFoods((prev) => [...prev, { ...food, id: crypto.randomUUID() }]);
        addToast(`${food.foodName} añadido`, 'success');
    };

    const removeFoodFromList = (id: string) => {
        setFoods((prev) => prev.filter((f) => f.id !== id));
    };

    const handleSave = () => {
        if (foods.length === 0) {
            addToast('Añade al menos un alimento.', 'danger');
            return;
        }

        const newLog: NutritionLog = {
            id: crypto.randomUUID(),
            date: safeCreateISOStringFromDateInput(logDate),
            mealType,
            foods,
            notes,
            status: 'consumed',
        };

        onSave(newLog);
        addToast('Comida registrada.', 'success');

        const dailyGoal = settings.dailyCalorieGoal || 2500;
        const mealCals = totalMacros.calories;
        const runningRatio = mealCals / (dailyGoal / 4);

        if (runningRatio < 0.6) {
            setTimeout(() => addToast('Déficit detectado: la recuperación puede ser más lenta.', 'suggestion'), 800);
        } else if (runningRatio > 1.3) {
            setTimeout(() => addToast('Superávit detectado: revisa que la proteína sea suficiente.', 'success'), 800);
        } else {
            setTimeout(() => addToast('AUGE ajustará tu recuperación con este registro.', 'suggestion'), 800);
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

    return (
        <>
            <TacticalModal
                isOpen={isOpen}
                onClose={onClose}
                variant="sheet"
                useCustomContent
                className="!bg-[var(--md-sys-color-surface)] !border !border-black/[0.08] !rounded-t-[28px]"
            >
                <div className="h-full flex flex-col bg-[var(--md-sys-color-surface)] text-[#1D1B20]">
                    <div className="px-4 pt-3 pb-2 border-b border-black/[0.06] bg-white/70">
                        <div className="mx-auto mb-2 h-1 w-12 rounded-full bg-black/10" />
                        <div className="flex items-center justify-between">
                            <p className="text-sm font-black tracking-tight">Registrar alimento</p>
                            <button onClick={onClose} className="text-[11px] font-black uppercase tracking-[0.14em] text-[#49454F]">
                                Cerrar
                            </button>
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
                        <section className="rounded-2xl border border-black/[0.06] bg-white/75 p-3">
                            <div className="flex items-center justify-between gap-2">
                                <input
                                    type="date"
                                    value={logDate}
                                    onChange={(e) => setLogDate(e.target.value)}
                                    className="rounded-xl border border-black/[0.1] bg-white px-2.5 py-1.5 text-xs text-[#1D1B20]"
                                />
                                <div className="flex gap-1">
                                    {mealOptions.map((opt) => (
                                        <button
                                            key={opt.id}
                                            onClick={() => setMealType(opt.id)}
                                            className={`px-2 py-1 rounded-lg text-[10px] font-black uppercase tracking-[0.12em] ${
                                                mealType === opt.id
                                                    ? 'bg-[var(--md-sys-color-primary)] text-white'
                                                    : 'bg-black/[0.05] text-[#49454F]'
                                            }`}
                                        >
                                            {opt.label.slice(0, 4)}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-2 mt-3">
                                <div className="rounded-xl border border-black/[0.06] bg-white/80 px-2.5 py-2">
                                    <p className="text-[9px] font-black uppercase tracking-[0.12em] text-[#49454F]">Calorías</p>
                                    <p className="text-sm font-black tabular-nums mt-0.5">{Math.round(totalMacros.calories)} kcal</p>
                                </div>
                                <div className="rounded-xl border border-black/[0.06] bg-white/80 px-2.5 py-2">
                                    <p className="text-[9px] font-black uppercase tracking-[0.12em] text-[#49454F]">Macros</p>
                                    <p className="text-[11px] font-black tabular-nums mt-0.5">P {Math.round(totalMacros.protein)} · C {Math.round(totalMacros.carbs)} · G {Math.round(totalMacros.fats)}</p>
                                </div>
                            </div>
                        </section>

                        <section className="rounded-2xl border border-black/[0.06] bg-white/75 p-3">
                            <div className="flex gap-2 mb-3">
                                <button
                                    onClick={() => setMode('search')}
                                    className={`flex-1 rounded-xl px-3 py-2 text-[11px] font-black uppercase tracking-[0.14em] ${mode === 'search' ? 'bg-white border border-black/[0.08] text-[#1D1B20]' : 'text-[#49454F] bg-black/[0.04]'}`}
                                >
                                    Buscar
                                </button>
                                <button
                                    onClick={() => setMode('pantry')}
                                    className={`flex-1 rounded-xl px-3 py-2 text-[11px] font-black uppercase tracking-[0.14em] ${mode === 'pantry' ? 'bg-white border border-black/[0.08] text-[#1D1B20]' : 'text-[#49454F] bg-black/[0.04]'}`}
                                >
                                    Despensa
                                </button>
                            </div>

                            {mode === 'search' ? (
                                <button
                                    onClick={() => setIsSearchModalOpen(true)}
                                    className="w-full flex items-center gap-2 rounded-xl border border-black/[0.1] bg-white px-3 py-2.5 text-left text-sm text-[#49454F]"
                                >
                                    <SearchIcon size={15} className="text-[#49454F]" />
                                    Buscar alimento en base externa
                                </button>
                            ) : (
                                <select
                                    onChange={(e) => {
                                        const item = pantryItems.find((p) => p.id === e.target.value);
                                        if (!item) return;
                                        addFoodToList({
                                            foodName: item.name,
                                            amount: 100,
                                            unit: item.unit,
                                            calories: item.calories,
                                            protein: item.protein,
                                            carbs: item.carbs,
                                            fats: item.fats,
                                        });
                                    }}
                                    className="w-full rounded-xl border border-black/[0.1] bg-white px-3 py-2.5 text-sm"
                                    defaultValue=""
                                >
                                    <option value="">Selecciona de tu despensa</option>
                                    {pantryItems.map((item) => (
                                        <option key={item.id} value={item.id}>{item.name}</option>
                                    ))}
                                </select>
                            )}
                        </section>

                        <section className="rounded-2xl border border-black/[0.06] bg-white/75 p-3">
                            <p className="text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-2">Alimentos agregados</p>
                            {foods.length === 0 ? (
                                <p className="text-[12px] text-[#49454F]">Aún no has agregado alimentos en esta comida.</p>
                            ) : (
                                <div className="space-y-2 max-h-[180px] overflow-y-auto pr-1">
                                    {foods.map((food) => (
                                        <div key={food.id} className="rounded-xl border border-black/[0.06] bg-white/85 px-3 py-2 flex items-start justify-between gap-2">
                                            <div className="min-w-0">
                                                <p className="text-[12px] font-semibold text-[#1D1B20] truncate">{food.foodName}</p>
                                                <p className="text-[11px] text-[#49454F] mt-0.5">
                                                    {food.amount}{food.unit} · {Math.round(food.calories)} kcal
                                                </p>
                                            </div>
                                            <button
                                                onClick={() => removeFoodFromList(food.id)}
                                                className="w-7 h-7 rounded-lg border border-black/[0.08] text-[#8C1D18] flex items-center justify-center"
                                                aria-label="Eliminar alimento"
                                            >
                                                <TrashIcon size={12} />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </section>

                        <section className="rounded-2xl border border-black/[0.06] bg-white/75 p-3">
                            <label className="block text-[10px] font-black uppercase tracking-[0.14em] text-[#49454F] mb-1">Nota (opcional)</label>
                            <textarea
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                placeholder="Ejemplo: post entrenamiento, hambre alta, etc."
                                rows={2}
                                className="w-full rounded-xl border border-black/[0.1] bg-white px-3 py-2 text-[12px] text-[#1D1B20] resize-none"
                            />
                        </section>
                    </div>

                    <div className="px-4 py-3 border-t border-black/[0.06] bg-white/75">
                        <Button
                            onClick={handleSave}
                            className={`w-full !py-3 !rounded-2xl !text-xs font-black tracking-[0.14em] ${foods.length > 0 ? '!bg-[var(--md-sys-color-primary)] !text-white' : '!bg-black/20 !text-[#49454F]'}`}
                            disabled={foods.length === 0}
                        >
                            {foods.length > 0 ? 'GUARDAR REGISTRO' : 'AGREGA ALIMENTOS'}
                        </Button>
                    </div>
                </div>
            </TacticalModal>

            <FoodSearchModal
                isOpen={isSearchModalOpen}
                onClose={() => setIsSearchModalOpen(false)}
                onSelect={(logged) => {
                    addFoodToList(logged);
                    setIsSearchModalOpen(false);
                }}
            />
        </>
    );
};

export default AddNutritionLogModal;
