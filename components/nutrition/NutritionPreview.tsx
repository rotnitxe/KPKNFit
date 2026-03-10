import React from 'react';
import { FoodAnalysisResult } from '../../services/FoodAI';
import { SparklesIcon, DatabaseIcon, ZapIcon, AlertTriangleIcon } from '../icons';

interface NutritionPreviewProps {
    result: FoodAnalysisResult;
}

/**
 * NutritionPreview - Vista Previa de Resultados Nutricionales
 * 
 * Muestra el resultado del análisis de forma clara y concisa:
 * - Nombre del alimento y categoría
 * - Macros principales (calorías, proteína, carbs, grasas)
 * - Fuente del dato (caché/BD/heurística/IA)
 * - Advertencias si las hay
 */
const NutritionPreview: React.FC<NutritionPreviewProps> = ({ result }) => {
    const getSourceInfo = () => {
        switch (result.source) {
            case 'cache':
                return {
                    icon: <LightningIcon size={14} className="text-emerald-600" />,
                    label: 'Caché',
                    description: 'Resultado guardado anteriormente',
                    bg: 'bg-emerald-50',
                    border: 'border-emerald-200',
                };
            case 'database':
                return {
                    icon: <DatabaseIcon size={14} className="text-blue-600" />,
                    label: 'Base de Datos',
                    description: 'Coincidencia exacta en USDA/OFF',
                    bg: 'bg-blue-50',
                    border: 'border-blue-200',
                };
            case 'heuristic':
                return {
                    icon: <ZapIcon size={14} className="text-amber-600" />,
                    label: 'Heurística',
                    description: 'Alimento común conocido',
                    bg: 'bg-amber-50',
                    border: 'border-amber-200',
                };
            case 'ai':
                return {
                    icon: <SparklesIcon size={14} className="text-purple-600" />,
                    label: 'IA',
                    description: 'Analizado con Qwen2.5',
                    bg: 'bg-purple-50',
                    border: 'border-purple-200',
                };
            default:
                return {
                    icon: <AlertTriangleIcon size={14} className="text-zinc-600" />,
                    label: 'Estimado',
                    description: 'Sin datos precisos',
                    bg: 'bg-zinc-50',
                    border: 'border-zinc-200',
                };
        }
    };

    const sourceInfo = getSourceInfo();
    const hasMacros = result.nutrition.calories !== null || result.nutrition.protein !== null;

    return (
        <div className="px-5 py-4 space-y-4">
            {/* Fuente del dato */}
            <div className={`flex items-center gap-2 px-3 py-2 rounded-xl border ${sourceInfo.bg} ${sourceInfo.border}`}>
                {sourceInfo.icon}
                <div className="flex-1">
                    <p className="text-[9px] font-black uppercase tracking-wider text-zinc-700">
                        {sourceInfo.label}
                    </p>
                    <p className="text-[8px] text-zinc-500">
                        {sourceInfo.description}
                    </p>
                </div>
                
                {/* Confianza */}
                <div className="text-right">
                    <p className="text-[8px] font-black uppercase tracking-wider text-zinc-400">
                        Confianza
                    </p>
                    <p className={`text-sm font-black ${
                        result.confidence >= 0.8 ? 'text-emerald-600' :
                        result.confidence >= 0.6 ? 'text-amber-600' :
                        'text-red-600'
                    }`}>
                        {Math.round(result.confidence * 100)}%
                    </p>
                </div>
            </div>

            {/* Nombre y categoría */}
            <div>
                <h3 className="text-base font-black text-zinc-900">
                    {result.foodName}
                </h3>
                <p className="text-[9px] uppercase tracking-wider text-zinc-500 mt-0.5">
                    {result.category.replace('_', ' ')}
                </p>
            </div>

            {/* Macros */}
            {hasMacros && (
                <div className="grid grid-cols-4 gap-3">
                    {/* Calorías */}
                    <div className="text-center p-3 rounded-2xl bg-gradient-to-br from-orange-50 to-orange-100 border border-orange-200">
                        <p className="text-xl font-black text-orange-600">
                            {result.nutrition.calories ?? '—'}
                        </p>
                        <p className="text-[7px] font-black uppercase tracking-wider text-orange-600 mt-0.5">
                            kcal
                        </p>
                    </div>

                    {/* Proteína */}
                    <div className="text-center p-3 rounded-2xl bg-gradient-to-br from-blue-50 to-blue-100 border border-blue-200">
                        <p className="text-xl font-black text-blue-600">
                            {result.nutrition.protein ?? '—'}
                        </p>
                        <p className="text-[7px] font-black uppercase tracking-wider text-blue-600 mt-0.5">
                            g P
                        </p>
                    </div>

                    {/* Carbs */}
                    <div className="text-center p-3 rounded-2xl bg-gradient-to-br from-amber-50 to-amber-100 border border-amber-200">
                        <p className="text-xl font-black text-amber-600">
                            {result.nutrition.carbs ?? '—'}
                        </p>
                        <p className="text-[7px] font-black uppercase tracking-wider text-amber-600 mt-0.5">
                            g C
                        </p>
                    </div>

                    {/* Grasas */}
                    <div className="text-center p-3 rounded-2xl bg-gradient-to-br from-purple-50 to-purple-100 border border-purple-200">
                        <p className="text-xl font-black text-purple-600">
                            {result.nutrition.fats ?? '—'}
                        </p>
                        <p className="text-[7px] font-black uppercase tracking-wider text-purple-600 mt-0.5">
                            g G
                        </p>
                    </div>
                </div>
            )}

            {/* Sin macros */}
            {!hasMacros && (
                <div className="px-4 py-3 rounded-2xl bg-zinc-50 border border-zinc-200 text-center">
                    <p className="text-[9px] font-black uppercase tracking-wider text-zinc-500">
                        Sin datos nutricionales precisos
                    </p>
                    <p className="text-[8px] text-zinc-400 mt-1">
                        Revisá manualmente si es necesario
                    </p>
                </div>
            )}

            {/* Advertencias */}
            {result.warnings.length > 0 && (
                <div className="px-4 py-3 rounded-2xl bg-amber-50 border border-amber-200">
                    <div className="flex items-start gap-2">
                        <AlertTriangleIcon size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                        <div className="flex-1">
                            <p className="text-[9px] font-black uppercase tracking-wider text-amber-700">
                                Advertencias
                            </p>
                            <ul className="text-[9px] text-amber-600 mt-1 space-y-0.5">
                                {result.warnings.map((warning, i) => (
                                    <li key={i}>• {warning}</li>
                                ))}
                            </ul>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default NutritionPreview;
