
// components/FoodDatabaseView.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { FoodItem } from '../types';
import { ArrowLeftIcon, PlusIcon, UtensilsIcon, PencilIcon, ChevronRightIcon, ActivityIcon } from './icons';
import Button from './ui/Button';

type SortKey = 'name' | 'protein' | 'carbs' | 'fats';
type Micronutrient = 'Hierro' | 'Calcio' | 'Vitamina D' | 'Magnesio';

const getMacronutrientCategory = (food: FoodItem) => {
    const totalCalories = food.calories;
    if (totalCalories === 0) return 'Otros';
    const proteinCalories = food.protein * 4;
    const carbCalories = food.carbs * 4;
    const fatCalories = food.fats * 9;
    const totalMacroCalories = Math.max(1, proteinCalories + carbCalories + fatCalories);

    if (proteinCalories / totalMacroCalories > 0.4) return 'Proteínas';
    if (carbCalories / totalMacroCalories > 0.4) return 'Carbohidratos';
    if (fatCalories / totalMacroCalories > 0.4) return 'Grasas';
    return 'Mixtos';
};

const FoodCard: React.FC<{ food: FoodItem; onEdit: (food: FoodItem) => void; onAddToPantry: (food: FoodItem) => void; }> = ({ food, onEdit, onAddToPantry }) => (
    <div
        className="p-4 flex justify-between items-center bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all duration-200 group active:scale-[0.99]"
        title={food.micronutrients?.length ? `Micronutrientes (por 100g): ${food.micronutrients.map(m => `${m.name} ${m.amount}${m.unit}`).join(', ')}` : undefined}
    >
        <div className="flex items-center gap-4 flex-grow min-w-0">
            {food.image ? (
                <img src={food.image} alt={food.name} className="w-10 h-10 rounded-lg object-cover flex-shrink-0 bg-slate-800" />
            ) : (
                <div className="w-10 h-10 rounded-lg bg-slate-800 flex items-center justify-center flex-shrink-0 text-slate-500">
                    <UtensilsIcon size={16} />
                </div>
            )}
            <div className="min-w-0">
                <p className="font-bold text-white text-sm truncate group-hover:text-cyber-copper transition-colors">{food.name}</p>
                <div className="flex gap-2 text-[10px] text-slate-400 mt-0.5 font-mono">
                    <span className="text-green-400">{Math.round(food.calories)}kcal</span>
                    <span>P:{food.protein}</span>
                    <span>C:{food.carbs}</span>
                    <span>F:{food.fats}</span>
                </div>
            </div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
            <button onClick={(e) => { e.stopPropagation(); onAddToPantry(food); }} className="p-2 text-slate-500 hover:text-green-400 bg-slate-800/50 rounded-lg"><PlusIcon size={14}/></button>
            <button onClick={(e) => { e.stopPropagation(); onEdit(food); }} className="p-2 text-slate-500 hover:text-white bg-slate-800/50 rounded-lg"><PencilIcon size={14}/></button>
        </div>
    </div>
);

const CategoryCard: React.FC<{ category: string, onClick: () => void, colorClass: string, count: number }> = ({ category, onClick, colorClass, count }) => (
    <div
        onClick={onClick}
        className={`relative overflow-hidden rounded-2xl p-4 cursor-pointer transition-all duration-300 hover:scale-[1.02] border border-white/5 group ${colorClass}`}
    >
        <div className="relative z-10 flex justify-between items-center">
            <div>
                 <h3 className="font-bold text-white text-lg">{category}</h3>
                 <p className="text-xs text-white/60 font-medium">{count} alimentos</p>
            </div>
            <ChevronRightIcon className="text-white/40 group-hover:text-white transition-colors" />
        </div>
    </div>
);

const FoodDatabaseView: React.FC = () => {
    const { foodDatabase, settings, searchQuery, isOnline } = useAppState();
    const { handleBack, openFoodEditor, openAddPantryItemModal, addToast, setCurrentBackgroundOverride } = useAppDispatch();
    const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
    const [selectedMicronutrient, setSelectedMicronutrient] = useState<Micronutrient | null>(null);
    const [sortKey, setSortKey] = useState<SortKey>('name');

    useEffect(() => {
        // Reset background when entering this view
        setCurrentBackgroundOverride(undefined);
    }, [setCurrentBackgroundOverride]);

    const categorizedFood = useMemo(() => {
        // Filter using GLOBAL searchQuery from context
        const query = searchQuery.toLowerCase();
        const filtered = query ? foodDatabase.filter(food =>
            food.name.toLowerCase().includes(query) ||
            (food.brand && food.brand.toLowerCase().includes(query))
        ) : foodDatabase;

        const categories: Record<string, FoodItem[]> = {
            'Proteínas': [], 'Carbohidratos': [], 'Grasas': [], 'Mixtos': [], 'Otros': [],
        };

        filtered.forEach(food => {
            const category = getMacronutrientCategory(food);
            if (categories[category]) {
                categories[category].push(food);
            } else {
                categories['Otros'].push(food);
            }
        });
        
        return { categories, allFiltered: filtered };
    }, [foodDatabase, searchQuery]);
    
    // Unified Visual Style Colors
    const categoryStyles: Record<string, string> = {
        'Proteínas': 'bg-gradient-to-br from-blue-900/40 to-blue-800/20 hover:border-blue-500/30',
        'Carbohidratos': 'bg-gradient-to-br from-cyber-copper/40 to-cyber-copper/20 hover:border-cyber-copper/30',
        'Grasas': 'bg-gradient-to-br from-yellow-900/40 to-yellow-800/20 hover:border-yellow-500/30',
        'Mixtos': 'bg-gradient-to-br from-purple-900/40 to-purple-800/20 hover:border-purple-500/30',
        'Otros': 'bg-slate-800/40 hover:border-slate-500/30',
    };
    
    const micronutrients: Micronutrient[] = ['Hierro', 'Calcio', 'Vitamina D', 'Magnesio'];
    
    // RENDER: Search Results (Global Search)
    if (searchQuery.length > 0) {
        return (
            <div className="pt-4 pb-32 px-4 max-w-4xl mx-auto animate-fade-in">
                 <div className="mb-6">
                    <h1 className="text-4xl font-black uppercase tracking-tighter text-white">Alimentos</h1>
                    <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mt-1">Resultados de búsqueda ({categorizedFood.allFiltered.length})</p>
                </div>
                <div className="space-y-2">
                    {categorizedFood.allFiltered.length > 0 ? (
                        categorizedFood.allFiltered.map(food => (
                             <FoodCard 
                                key={food.id} 
                                food={food} 
                                onEdit={(f) => openFoodEditor({ food: f })}
                                onAddToPantry={(f) => openAddPantryItemModal(f)}
                            />
                        ))
                    ) : (
                        <div className="text-center py-10 opacity-50">
                            <UtensilsIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                            <p className="text-slate-400">No se encontraron alimentos.</p>
                        </div>
                    )}
                </div>
            </div>
        );
    }
    
    // RENDER: Selected Micronutrient View
    if (selectedMicronutrient) {
        const foods = foodDatabase
            .filter(f => f.micronutrients?.some(m => m.name === selectedMicronutrient))
            .sort((a, b) => {
                const aAmount = a.micronutrients?.find(m => m.name === selectedMicronutrient)?.amount || 0;
                const bAmount = b.micronutrients?.find(m => m.name === selectedMicronutrient)?.amount || 0;
                return bAmount - aAmount;
            });

        return (
            <div className="pt-4 pb-32 px-4 max-w-4xl mx-auto animate-fade-in">
                 <header className="flex items-center gap-4 mb-6">
                    <button onClick={() => setSelectedMicronutrient(null)} className="p-2 -ml-2 text-slate-400 hover:text-white transition-colors"><ArrowLeftIcon /></button>
                    <div>
                        <h2 className="text-2xl font-bold text-white">Ricos en {selectedMicronutrient}</h2>
                        <p className="text-xs text-slate-400 font-bold uppercase tracking-widest">Fuentes Principales</p>
                    </div>
                </header>
                <div className="space-y-2">
                    {foods.map(food => (
                         <FoodCard 
                            key={food.id} 
                            food={food} 
                            onEdit={(f) => openFoodEditor({ food: f })}
                            onAddToPantry={(f) => openAddPantryItemModal(f)}
                        />
                    ))}
                    {foods.length === 0 && <p className="text-slate-500 text-center py-8">No hay alimentos etiquetados con este micronutriente.</p>}
                </div>
            </div>
        )
    }

    // RENDER: Selected Category View
    if (selectedCategory) {
        const foods = [...(categorizedFood.categories[selectedCategory] || [])].sort((a, b) => {
            if (sortKey === 'name') return a.name.localeCompare(b.name);
            return (b[sortKey] || 0) - (a[sortKey] || 0);
        });

        return (
            <div className="pt-4 pb-32 px-4 max-w-4xl mx-auto animate-fade-in">
                 <header className="flex items-center gap-4 mb-6">
                    <button onClick={() => setSelectedCategory(null)} className="p-2 -ml-2 text-slate-400 hover:text-white transition-colors"><ArrowLeftIcon /></button>
                    <div>
                        <h2 className="text-3xl font-black text-white uppercase tracking-tight">{selectedCategory}</h2>
                    </div>
                </header>
                
                <div className="space-y-4">
                    <div className="glass-card-nested p-4 rounded-xl relative overflow-hidden">
                        <div className="absolute top-0 right-0 p-4 opacity-10"><UtensilsIcon size={64}/></div>
                        <p className="text-sm text-slate-300 italic relative z-10">Alimentos ricos en {selectedCategory.toLowerCase()}.</p>
                    </div>

                    <div className="flex gap-2 overflow-x-auto pb-2 hide-scrollbar">
                        <button onClick={() => setSortKey('name')} className={`px-4 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-all border ${sortKey === 'name' ? 'bg-white text-black border-white' : 'bg-transparent text-slate-400 border-slate-700'}`}>A-Z</button>
                        {selectedCategory === 'Proteínas' && <button onClick={() => setSortKey('protein')} className={`px-4 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-all border ${sortKey === 'protein' ? 'bg-blue-500 text-white border-blue-500' : 'bg-transparent text-slate-400 border-slate-700'}`}>Mayor Proteína</button>}
                        {selectedCategory === 'Carbohidratos' && <button onClick={() => setSortKey('carbs')} className={`px-4 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-all border ${sortKey === 'carbs' ? 'bg-cyber-copper text-white border-cyber-copper' : 'bg-transparent text-slate-400 border-slate-700'}`}>Más Carbs</button>}
                        {selectedCategory === 'Grasas' && <button onClick={() => setSortKey('fats')} className={`px-4 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-all border ${sortKey === 'fats' ? 'bg-yellow-500 text-white border-yellow-500' : 'bg-transparent text-slate-400 border-slate-700'}`}>Más Grasa</button>}
                    </div>

                    <div className="space-y-2">
                        {foods.map(food => (
                            <FoodCard 
                                key={food.id} 
                                food={food} 
                                onEdit={(f) => openFoodEditor({ food: f })}
                                onAddToPantry={(f) => openAddPantryItemModal(f)}
                            />
                        ))}
                    </div>
                </div>
            </div>
        )
    }

    // RENDER: Main Dashboard
    return (
        <div className="pt-4 pb-32 px-4 max-w-4xl mx-auto">
            <div className="mb-6">
                <h1 className="text-4xl font-black uppercase tracking-tighter text-white">Alimentos</h1>
                <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mt-1">Base de Datos Nutricional</p>
            </div>
            
            <div className="space-y-8 animate-fade-in-up">
                <div>
                    <h2 className="text-sm font-black text-slate-500 uppercase tracking-widest mb-3 flex items-center gap-2"><ActivityIcon size={14}/> Macronutrientes</h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        {Object.entries(categorizedFood.categories).map(([category, foods]) => {
                            if ((foods as FoodItem[]).length === 0) return null;
                            return (
                                <CategoryCard
                                    key={category}
                                    category={category}
                                    onClick={() => setSelectedCategory(category)}
                                    colorClass={categoryStyles[category] || categoryStyles['Otros']}
                                    count={(foods as FoodItem[]).length}
                                />
                            )
                        })}
                    </div>
                </div>

                <div>
                    <h2 className="text-sm font-black text-slate-500 uppercase tracking-widest mb-3 flex items-center gap-2"><ActivityIcon size={14}/> Micronutrientes Clave</h2>
                    <div className="grid grid-cols-2 gap-3">
                        {micronutrients.map(micro => (
                             <div 
                                key={micro}
                                onClick={() => setSelectedMicronutrient(micro)}
                                className="bg-slate-900/30 border border-white/5 rounded-xl p-3 cursor-pointer hover:bg-slate-800 transition-colors group"
                            >
                                <p className="text-white font-bold group-hover:text-primary-color transition-colors">{micro}</p>
                                <p className="text-center text-[10px] text-slate-500 uppercase tracking-wider">{foodDatabase.filter(f => f.micronutrients?.some(m => m.name === micro)).length} fuentes</p>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default FoodDatabaseView;
