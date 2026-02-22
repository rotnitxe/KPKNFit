// components/SmartMealPlannerView.tsx
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { PantryItem } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { ArrowLeftIcon, PlusIcon, TrashIcon } from './icons';

const SmartMealPlannerView: React.FC = () => {
    const { pantryItems } = useAppState();
    const { setPantryItems, handleBack, addToast } = useAppDispatch();
    
    const [newItemName, setNewItemName] = useState('');
    const [newItem, setNewItem] = useState<Omit<PantryItem, 'id'>>({ name: '', calories: 0, protein: 0, carbs: 0, fats: 0, currentQuantity: 0, unit: 'g' });

    const handleAddItem = () => {
        if (!newItem.name.trim() || newItem.calories <= 0 || newItem.currentQuantity <= 0) {
            addToast("El nombre, las calorías y la cantidad inicial son obligatorios.", "danger");
            return;
        }
        const item: PantryItem = { id: crypto.randomUUID(), ...newItem };
        setPantryItems(prev => [...prev, item]);
        setNewItemName('');
        setNewItem({ name: '', calories: 0, protein: 0, carbs: 0, fats: 0, currentQuantity: 0, unit: 'g' });
    };

    const handleRemoveItem = (id: string) => {
        if (window.confirm("¿Seguro que quieres eliminar este alimento de tu despensa?")) {
            setPantryItems(prev => prev.filter(item => item.id !== id));
        }
    };
    
    const handleUpdateItem = (id: string, field: keyof Omit<PantryItem, 'id'|'name'|'unit'>, value: string) => {
        setPantryItems(prev => prev.map(item => item.id === id ? { ...item, [field]: parseFloat(value) || 0 } : item));
    };

    const handleNameChange = (name: string) => {
        setNewItemName(name);
        setNewItem(prev => ({ ...prev, name }));
    };

    return (
        <div className="pt-[65px] pb-20 animate-fade-in">
             <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                <button onClick={handleBack} className="p-2 text-slate-300"><ArrowLeftIcon /></button>
                <div>
                    <h1 className="text-3xl font-bold text-white">Planificador Inteligente</h1>
                    <p className="text-slate-400 text-sm">Gestiona tu despensa y obtén sugerencias de comidas.</p>
                </div>
            </header>
            <div className="space-y-6">
                <Card>
                    <h2 className="text-xl font-bold text-white mb-3">Tu Despensa</h2>
                    <div className="space-y-2 max-h-60 overflow-y-auto pr-2 mb-4">
                        {pantryItems.map(item => (
                            <details key={item.id} className="glass-card-nested !p-2">
                                <summary className="flex justify-between items-center cursor-pointer">
                                    <div className="font-semibold">{item.name} <span className="text-slate-400 font-normal">({item.currentQuantity}{item.unit})</span></div>
                                    <button onClick={(e) => { e.stopPropagation(); handleRemoveItem(item.id);}}><TrashIcon size={16} className="text-slate-500 hover:text-red-400"/></button>
                                </summary>
                                <div className="grid grid-cols-5 gap-2 text-xs mt-2">
                                    <div><label>Cant.</label><input type="number" value={item.currentQuantity} onChange={e => handleUpdateItem(item.id, 'currentQuantity', e.target.value)} className="w-full text-center" /></div>
                                    <div><label>Cal/100</label><input type="number" value={item.calories} onChange={e => handleUpdateItem(item.id, 'calories', e.target.value)} className="w-full text-center" /></div>
                                    <div><label>Prot/100</label><input type="number" value={item.protein} onChange={e => handleUpdateItem(item.id, 'protein', e.target.value)} className="w-full text-center" /></div>
                                    <div><label>Carb/100</label><input type="number" value={item.carbs} onChange={e => handleUpdateItem(item.id, 'carbs', e.target.value)} className="w-full text-center" /></div>
                                    <div><label>Gras/100</label><input type="number" value={item.fats} onChange={e => handleUpdateItem(item.id, 'fats', e.target.value)} className="w-full text-center" /></div>
                                </div>
                            </details>
                        ))}
                         {pantryItems.length === 0 && <p className="text-center text-slate-500 py-4">Tu despensa está vacía.</p>}
                    </div>
                    <div className="space-y-3 pt-3 border-t border-slate-700">
                        <input type="text" value={newItemName} onChange={e => handleNameChange(e.target.value)} placeholder="Nombre del nuevo alimento" className="w-full"/>
                        {newItemName.trim() && (
                            <div className="grid grid-cols-5 gap-2 text-xs animate-fade-in">
                                <div><label>Cant.</label><input type="number" value={newItem.currentQuantity} onChange={e => setNewItem(p => ({...p, currentQuantity: parseFloat(e.target.value) || 0}))} className="w-full text-center" /></div>
                                <div><label>Cal/100</label><input type="number" value={newItem.calories} onChange={e => setNewItem(p => ({...p, calories: parseFloat(e.target.value) || 0}))} className="w-full text-center" /></div>
                                <div><label>Prot/100</label><input type="number" value={newItem.protein} onChange={e => setNewItem(p => ({...p, protein: parseFloat(e.target.value) || 0}))} className="w-full text-center" /></div>
                                <div><label>Carb/100</label><input type="number" value={newItem.carbs} onChange={e => setNewItem(p => ({...p, carbs: parseFloat(e.target.value) || 0}))} className="w-full text-center" /></div>
                                <div><label>Gras/100</label><input type="number" value={newItem.fats} onChange={e => setNewItem(p => ({...p, fats: parseFloat(e.target.value) || 0}))} className="w-full text-center" /></div>
                            </div>
                        )}
                        <Button onClick={handleAddItem} className="w-full"><PlusIcon/> Añadir a Despensa</Button>
                    </div>
                </Card>
            </div>
        </div>
    );
};

export default SmartMealPlannerView;