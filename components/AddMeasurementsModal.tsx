// components/AddMeasurementsModal.tsx
import React, { useState, useEffect } from 'react';
import { BiomechanicalData } from '../types';
import { useAppState } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon } from './icons';
import { InfoTooltip } from './ui/InfoTooltip';

interface AddMeasurementsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (data: BiomechanicalData) => Promise<void>;
}

const MeasurementInput: React.FC<{
    label: string;
    value: string;
    onChange: (value: string) => void;
    tooltip: string;
}> = ({ label, value, onChange, tooltip }) => (
    <div>
        <label className="block text-sm font-medium text-slate-300 mb-1 flex items-center gap-1">
            {label} (cm)
            <InfoTooltip term={tooltip} />
        </label>
        <input
            type="number"
            step="0.1"
            value={value}
            onChange={e => onChange(e.target.value)}
            className="w-full bg-slate-800 border border-slate-700 rounded-md p-2"
        />
    </div>
);

const AddMeasurementsModal: React.FC<AddMeasurementsModalProps> = ({ isOpen, onClose, onSave }) => {
    const { biomechanicalData } = useAppState();
    const [isLoading, setIsLoading] = useState(false);
    const [data, setData] = useState<Record<keyof BiomechanicalData, string>>({
        height: '', wingspan: '', torsoLength: '', femurLength: '',
        tibiaLength: '', humerusLength: '', forearmLength: ''
    });

    useEffect(() => {
        if (biomechanicalData) {
            setData({
                height: biomechanicalData.height?.toString() || '',
                wingspan: biomechanicalData.wingspan?.toString() || '',
                torsoLength: biomechanicalData.torsoLength?.toString() || '',
                femurLength: biomechanicalData.femurLength?.toString() || '',
                tibiaLength: biomechanicalData.tibiaLength?.toString() || '',
                humerusLength: biomechanicalData.humerusLength?.toString() || '',
                forearmLength: biomechanicalData.forearmLength?.toString() || '',
            });
        }
    }, [biomechanicalData]);

    const handleChange = (key: keyof BiomechanicalData, value: string) => {
        setData(prev => ({ ...prev, [key]: value }));
    };

    const handleSave = async () => {
        const parsedData: BiomechanicalData = {
            height: parseFloat(data.height) || 0,
            wingspan: parseFloat(data.wingspan) || 0,
            torsoLength: parseFloat(data.torsoLength) || 0,
            femurLength: parseFloat(data.femurLength) || 0,
            tibiaLength: parseFloat(data.tibiaLength) || 0,
            humerusLength: parseFloat(data.humerusLength) || 0,
            forearmLength: parseFloat(data.forearmLength) || 0,
        };

        // Fix: Use typed check to ensure values are numbers
        const values = Object.values(parsedData) as number[];
        if (values.some(v => v <= 0)) {
            alert("Por favor, introduce valores válidos y positivos para todas las medidas.");
            return;
        }
        
        setIsLoading(true);
        try {
            await onSave(parsedData);
            onClose();
        } catch (error) {
            console.error("Failed to save and analyze biomechanical data:", error);
            // The context will show an error toast.
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Añadir Medidas Biomecánicas">
            <div className="space-y-4 max-h-[70vh] overflow-y-auto p-1">
                <p className="text-sm text-slate-400">Introduce tus medidas corporales para que la IA pueda analizar tu biomecánica y ofrecerte recomendaciones personalizadas.</p>
                <div className="grid grid-cols-2 gap-4">
                    <MeasurementInput label="Altura" value={data.height} onChange={v => handleChange('height', v)} tooltip="Tu altura total en centímetros." />
                    <MeasurementInput label="Envergadura" value={data.wingspan} onChange={v => handleChange('wingspan', v)} tooltip="Distancia de punta a punta de los dedos con los brazos extendidos en 'T'." />
                    <MeasurementInput label="Long. Torso" value={data.torsoLength} onChange={v => handleChange('torsoLength', v)} tooltip="Desde el hueco sobre el esternón hasta el pliegue de la cadera al sentarse." />
                    <MeasurementInput label="Long. Fémur" value={data.femurLength} onChange={v => handleChange('femurLength', v)} tooltip="Desde el pliegue de la cadera hasta la parte superior de la rodilla." />
                    <MeasurementInput label="Long. Tibia" value={data.tibiaLength} onChange={v => handleChange('tibiaLength', v)} tooltip="Desde la parte inferior de la rodilla hasta el hueso del tobillo." />
                    <MeasurementInput label="Long. Húmero" value={data.humerusLength} onChange={v => handleChange('humerusLength', v)} tooltip="Desde el hueso del hombro hasta el codo." />
                    <MeasurementInput label="Long. Antebrazo" value={data.forearmLength} onChange={v => handleChange('forearmLength', v)} tooltip="Desde el codo hasta la muñeca." />
                </div>
                <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                    <Button variant="secondary" onClick={onClose} disabled={isLoading}>Cancelar</Button>
                    <Button onClick={handleSave} isLoading={isLoading} disabled={isLoading}><SaveIcon /> Guardar y Analizar</Button>
                </div>
            </div>
        </Modal>
    );
};

export default AddMeasurementsModal;
