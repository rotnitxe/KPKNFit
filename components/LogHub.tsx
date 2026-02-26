
// components/LogHub.tsx
import React from 'react';
import Card from './ui/Card';
import { PlayIcon, BodyIcon, UtensilsIcon } from './icons';
import { View } from '../types';
import { useAppDispatch } from '../contexts/AppContext';

interface LogHubProps {
  onNavigate: (view: View) => void;
  setIsBodyLogModalOpen: (isOpen: boolean) => void;
  setIsNutritionLogModalOpen: (isOpen: boolean) => void;
}

const LogHub: React.FC<LogHubProps> = ({ onNavigate, setIsBodyLogModalOpen, setIsNutritionLogModalOpen }) => {
  const { setIsStartWorkoutModalOpen } = useAppDispatch();
  return (
    <div className="animate-fade-in space-y-8 max-w-2xl mx-auto text-center tab-bar-safe-area">
      <div>
        <h1 className="text-4xl font-bold uppercase tracking-wider">Registrar</h1>
      </div>
      <div className="space-y-6">
        <Card
          onClick={() => setIsStartWorkoutModalOpen(true)}
          className="cursor-pointer hover:border-primary-color transition-all duration-300 transform hover:scale-105 border-2 border-transparent"
        >
          <div className="flex flex-col items-center p-4">
            <PlayIcon size={40} className="mb-3 text-primary-color" />
            <h3 className="text-2xl font-bold text-white">Iniciar Entrenamiento</h3>
            <p className="text-slate-400 mt-1">Empieza una sesión de uno de tus programas.</p>
          </div>
        </Card>
        <Card
          onClick={() => setIsBodyLogModalOpen(true)}
          className="cursor-pointer hover:border-sky-500 transition-all duration-300 transform hover:scale-105 border-2 border-transparent"
        >
          <div className="flex flex-col items-center p-4">
            <BodyIcon size={40} className="mb-3 text-sky-400" />
            <h3 className="text-2xl font-bold text-white">Métricas Corporales</h3>
            <p className="text-slate-400 mt-1">Registra tu peso, medidas, etc.</p>
          </div>
        </Card>
        <Card
          onClick={() => setIsNutritionLogModalOpen(true)}
          className="cursor-pointer hover:border-cyber-cyan transition-all duration-300 transform hover:scale-105 border-2 border-transparent"
        >
          <div className="flex flex-col items-center p-4">
            <UtensilsIcon size={40} className="mb-3 text-cyber-copper" />
            <h3 className="text-2xl font-bold text-white">Comida / Nutrición</h3>
            <p className="text-slate-400 mt-1">Registra una comida manualmente o con IA.</p>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default LogHub;
