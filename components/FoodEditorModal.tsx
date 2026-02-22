
// components/FoodEditorModal.tsx
import React, { useState, useEffect, useRef } from 'react';
import { FoodItem } from '../types';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon, UtensilsIcon, ActivityIcon } from './icons';

const FoodEditorModal: React.FC = () => {
    const { isFoodEditorOpen, editingFoodData } = useAppState();
    const { closeFoodEditor, addOrUpdateFoodItem, addToast } = useAppDispatch();

    const getInitialFoodState = (): Partial<FoodItem> => ({
        name: '', brand: '', servingSize: 100, unit: 'g', calories: 0, protein: 0, carbs: 0, fats: 0,
        fatBreakdown: { saturated: 0, monounsaturated: 0, polyunsaturated: 0, trans: 0 },
        carbBreakdown: { fiber: 0, sugar: 0 },
        proteinQuality: { completeness: 'Incompleta', details: '' },
        micronutrients: [],
        aiNotes: ''
    });

    const [food, setFood] = useState<Partial<FoodItem>>(getInitialFoodState());
    
    useEffect(() => {
        if (isFoodEditorOpen) {
            if (editingFoodData?.food) {
                setFood({ ...getInitialFoodState(), ...JSON.parse(JSON.stringify(editingFoodData.food)) });
            } else {
                setFood({ ...getInitialFoodState(), name: editingFoodData?.preFilledName || '' });
            }
        }
    }, [isFoodEditorOpen, editingFoodData]);

    const handleChange = (field: keyof Omit<FoodItem, 'id' | 'isCustom'>, value: any) => {
        setFood(prev => ({ ...prev, [field]: value }));
    };

    const handleSave = () => {
        if (!food.name?.trim()) {
            addToast("El nombre del alimento es obligatorio.", "danger");
            return;
        }
        addOrUpdateFoodItem({
            ...getInitialFoodState(),
            ...food,
            id: food.id || `custom_${crypto.randomUUID()}`,
            name: food.name!,
            isCustom: true
        } as FoodItem);
        closeFoodEditor();
    };

    return (
        <Modal isOpen={isFoodEditorOpen} onClose={closeFoodEditor} title={food.id ? "Editar Alimento" : "Nuevo Alimento"} useCustomContent={true}>
            {/* Contenedor Flex para layout personalizado */}
            <div className="flex flex-col h-full overflow-hidden">
                
                {/* Contenido Desplazable con Padding Inferior Extra (pb-32) para que el footer no tape */}
                <div className="flex-grow overflow-y-auto px-6 py-4 space-y-6 custom-scrollbar pb-32">
                    
                    {/* Nombre */}
                    <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Nombre del alimento</label>
                        <div className="relative group">
                            <UtensilsIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 group-focus-within:text-orange-400 transition-colors"/>
                            <input 
                                type="text" 
                                value={food.name || ''} 
                                onChange={e => handleChange('name', e.target.value)} 
                                placeholder="ej. Pechuga de Pollo"
                                className="w-full bg-slate-900/50 border border-white/10 rounded-2xl p-4 pl-12 font-bold text-white text-lg focus:border-orange-500 outline-none transition-all placeholder:text-slate-600"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                         <div className="space-y-1">
                            <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Marca / Fabricante</label>
                            <input 
                                type="text" 
                                value={food.brand || ''} 
                                onChange={e => handleChange('brand', e.target.value)} 
                                placeholder="ej. Casero, Marca..." 
                                className="w-full text-sm font-bold bg-slate-900/50 border border-white/5 rounded-xl p-3 text-slate-200 outline-none focus:border-white/20 transition-all" 
                            />
                        </div>
                        <div className="space-y-1">
                            <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Porción</label>
                            <div className="flex gap-2">
                                <input 
                                    type="number" 
                                    value={food.servingSize} 
                                    onChange={e => handleChange('servingSize', Number(e.target.value))} 
                                    className="w-full text-sm font-bold bg-slate-900/50 border border-white/5 rounded-xl p-3 text-white outline-none focus:border-white/20 text-center" 
                                />
                                <select 
                                    value={food.unit} 
                                    onChange={e => handleChange('unit', e.target.value)}
                                    className="w-20 bg-slate-900/50 border border-white/5 rounded-xl text-xs font-black text-slate-400 uppercase outline-none text-center"
                                >
                                    <option value="g">g</option>
                                    <option value="ml">ml</option>
                                    <option value="oz">oz</option>
                                    <option value="unit">ud</option>
                                </select>
                            </div>
                        </div>
                    </div>

                    {/* Macros Card */}
                    <div className="bg-slate-900/30 p-4 rounded-3xl border border-white/5 space-y-4 shadow-lg relative overflow-hidden">
                        {/* Decorative Background Blur */}
                        <div className="absolute top-0 right-0 w-32 h-32 bg-orange-500/5 blur-[40px] rounded-full pointer-events-none"></div>
                        
                        <h4 className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-2 flex items-center gap-2">
                            <ActivityIcon size={12}/> Información Nutricional
                        </h4>
                        
                        <div className="grid grid-cols-2 gap-3 relative z-10">
                            <div className="bg-slate-950 p-3 rounded-2xl border border-white/5 shadow-sm group hover:border-green-500/30 transition-colors">
                                <label className="block text-[8px] font-black text-green-500 uppercase mb-1 tracking-wider">Calorías (kcal)</label>
                                <input type="number" value={food.calories} onChange={e => handleChange('calories', Number(e.target.value))} className="w-full bg-transparent border-none font-mono text-2xl font-black text-white p-0 focus:ring-0 placeholder-slate-700" placeholder="0" />
                            </div>
                            <div className="bg-slate-950 p-3 rounded-2xl border border-white/5 shadow-sm group hover:border-blue-500/30 transition-colors">
                                <label className="block text-[8px] font-black text-blue-500 uppercase mb-1 tracking-wider">Proteínas (g)</label>
                                <input type="number" step="0.1" value={food.protein} onChange={e => handleChange('protein', Number(e.target.value))} className="w-full bg-transparent border-none font-mono text-2xl font-black text-white p-0 focus:ring-0 placeholder-slate-700" placeholder="0.0" />
                            </div>
                            <div className="bg-slate-950 p-3 rounded-2xl border border-white/5 shadow-sm group hover:border-orange-500/30 transition-colors">
                                <label className="block text-[8px] font-black text-orange-500 uppercase mb-1 tracking-wider">Carbohidratos (g)</label>
                                <input type="number" step="0.1" value={food.carbs} onChange={e => handleChange('carbs', Number(e.target.value))} className="w-full bg-transparent border-none font-mono text-2xl font-black text-white p-0 focus:ring-0 placeholder-slate-700" placeholder="0.0" />
                            </div>
                            <div className="bg-slate-950 p-3 rounded-2xl border border-white/5 shadow-sm group hover:border-yellow-500/30 transition-colors">
                                <label className="block text-[8px] font-black text-yellow-500 uppercase mb-1 tracking-wider">Grasas (g)</label>
                                <input type="number" step="0.1" value={food.fats} onChange={e => handleChange('fats', Number(e.target.value))} className="w-full bg-transparent border-none font-mono text-2xl font-black text-white p-0 focus:ring-0 placeholder-slate-700" placeholder="0.0" />
                            </div>
                        </div>
                    </div>

                    <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Notas Adicionales</label>
                        <textarea 
                            value={food.aiNotes || ''} 
                            onChange={e => handleChange('aiNotes', e.target.value)} 
                            placeholder="Detalles sobre micronutrientes, fibra, tipo de grasa..."
                            rows={3}
                            className="w-full bg-slate-900/50 border border-white/5 rounded-2xl p-4 text-xs text-slate-300 shadow-inner focus:border-orange-500/30 outline-none resize-none placeholder:text-slate-600" 
                        />
                    </div>
                </div>

                {/* Pie de Página Fijo (Sticky Footer) */}
                <div className="absolute bottom-0 left-0 right-0 p-4 pb-6 bg-[#0a0a0a]/90 backdrop-blur-xl border-t border-white/10 z-20 flex gap-3">
                    <Button onClick={closeFoodEditor} variant="secondary" className="flex-1 !py-3 uppercase font-bold text-xs !bg-slate-900 border-slate-800 text-slate-400 hover:text-white">
                        Cancelar
                    </Button>
                    <Button onClick={handleSave} className="flex-[2] !py-3 uppercase font-black text-xs shadow-lg shadow-orange-500/20 hover:shadow-orange-500/40 bg-orange-600 border-orange-500 hover:bg-orange-500 transition-all">
                        <SaveIcon size={16} className="mr-2"/> Guardar
                    </Button>
                </div>
            </div>
        </Modal>
    );
};

export default FoodEditorModal;
