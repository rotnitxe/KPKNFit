// components/AddPantryItemModal.tsx
import React, { useState, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { PantryItem } from '../types';
import Modal from './ui/Modal';
import Button from './ui/Button';

const AddPantryItemModal: React.FC = () => {
    const { isAddPantryItemModalOpen, foodItemToAdd_to_pantry } = useAppState();
    const { closeAddPantryItemModal, addOrUpdatePantryItem } = useAppDispatch();
    const [quantity, setQuantity] = useState('');

    useEffect(() => {
        if (!isAddPantryItemModalOpen) {
            setQuantity('');
        }
    }, [isAddPantryItemModalOpen]);

    const handleSave = () => {
        if (!foodItemToAdd_to_pantry) return;
        const amount = parseFloat(quantity);
        if (isNaN(amount) || amount <= 0) return;

        // Create a new PantryItem, assuming macros are per 100g/ml if not otherwise specified.
        // A more robust implementation would normalize here if servingSize is not 100.
        const ratio = foodItemToAdd_to_pantry.servingSize > 0 ? 100 / foodItemToAdd_to_pantry.servingSize : 1;

        const newItem: PantryItem = {
            id: foodItemToAdd_to_pantry.id, // Using food ID for simplicity to find/update
            name: foodItemToAdd_to_pantry.name,
            calories: Math.round(foodItemToAdd_to_pantry.calories * ratio),
            protein: Math.round((foodItemToAdd_to_pantry.protein * ratio) * 10) / 10,
            carbs: Math.round((foodItemToAdd_to_pantry.carbs * ratio) * 10) / 10,
            fats: Math.round((foodItemToAdd_to_pantry.fats * ratio) * 10) / 10,
            unit: foodItemToAdd_to_pantry.unit,
            currentQuantity: amount,
        };

        addOrUpdatePantryItem(newItem);
        setQuantity('');
        closeAddPantryItemModal();
    };

    return (
        <Modal isOpen={isAddPantryItemModalOpen} onClose={closeAddPantryItemModal} title={`Añadir a Despensa: ${foodItemToAdd_to_pantry?.name}`}>
            <div className="space-y-4">
                <p>Introduce la cantidad que quieres añadir a tu despensa.</p>
                <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">Cantidad ({foodItemToAdd_to_pantry?.unit})</label>
                    <input 
                        type="number"
                        value={quantity}
                        onChange={e => setQuantity(e.target.value)}
                        className="w-full"
                        autoFocus
                        placeholder="Ej: 500"
                    />
                </div>
                 <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                    <Button variant="secondary" onClick={closeAddPantryItemModal}>Cancelar</Button>
                    <Button onClick={handleSave}>Añadir</Button>
                </div>
            </div>
        </Modal>
    );
};

export default AddPantryItemModal;